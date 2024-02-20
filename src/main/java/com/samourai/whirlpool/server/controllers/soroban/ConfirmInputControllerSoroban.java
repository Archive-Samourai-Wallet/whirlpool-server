package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.sorobanClient.SorobanPayloadable;
import com.samourai.whirlpool.protocol.SorobanAppWhirlpool;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.soroban.payload.mix.ConfirmInputRequest;
import com.samourai.whirlpool.protocol.soroban.payload.mix.ConfirmInputResponse;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import com.samourai.whirlpool.server.services.ConfirmInputService;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfirmInputControllerSoroban extends AbstractPerMixControllerSoroban {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private ConfirmInputService confirmInputService;

  public ConfirmInputControllerSoroban(
      WhirlpoolServerContext serverContext,
      SorobanAppWhirlpool sorobanAppWhirlpool,
      ConfirmInputService confirmInputService,
      Mix mix) {
    super(
        serverContext,
        sorobanAppWhirlpool.getEndpointMixConfirmInput(
            mix.getMixId(), serverContext.getCoordinatorWallet().getPaymentCode()),
        mix,
        "CONFIRM_INPUT");
    this.confirmInputService = confirmInputService;
  }

  @Override
  protected SorobanPayloadable doComputeReplyOnRequestNewForCaching(SorobanItemTyped request)
      throws Exception {
    try {
      return confirmInput(request, request.read(ConfirmInputRequest.class));
    } catch (QueueInputException e) {
      // silently ignore (ie: mix is full...)
      return null;
    }
  }

  // throws QueueInputException
  protected ConfirmInputResponse confirmInput(SorobanItemTyped request, ConfirmInputRequest payload)
      throws Exception {
    PaymentCode sender = request.getMetaSender();
    RegisteredInput registeredInput =
        mix.removeConfirmingInputBySender(sender)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        WhirlpoolErrorCode.SERVER_ERROR,
                        "Confirming input not found: sender=" + sender.toString()));
    registeredInput.getSorobanInput().setSorobanLastSeen();

    // confirm input and send back signed bordereau, or enqueue back to pool
    byte[] blindedBordereau = WhirlpoolProtocol.decodeBytes(payload.blindedBordereau64);
    byte[] signedBordereau =
        confirmInputService.confirmInput(mix, registeredInput, blindedBordereau, payload.userHash);

    // reply confirmInputResponse with signedBordereau
    String signedBordereau64 = WhirlpoolProtocol.encodeBytes(signedBordereau);
    return new ConfirmInputResponse(signedBordereau64);
  }
}
