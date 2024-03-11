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
  protected SorobanPayloadable doComputeReply(SorobanItemTyped request) throws Exception {
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

    // check if already confirmed?
    RegisteredInput confirmedInput = mix.getInputs().findBySorobanSender(sender).orElse(null);
    if (confirmedInput != null) {
      // already confirmed (duplicate request)
      return confirmInputResponse(confirmedInput);
    }

    // check if confirming
    RegisteredInput confirmingInput =
        mix.getConfirmingInputs()
            .findBySorobanSender(sender)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        WhirlpoolErrorCode.MIX_OVER,
                        "Confirming input not found: sender=" + sender.toString()));
    // confirming => confirm input
    byte[] blindedBordereau = WhirlpoolProtocol.decodeBytes(payload.blindedBordereau64);
    confirmInputService.confirmInput(mix, confirmingInput, blindedBordereau, payload.userHash);

    // remove confirming input AFTER it was confirmed, to avoid SorobanController "confirming input
    // not found"
    mix.removeConfirmingInputBySender(sender);

    return confirmInputResponse(confirmingInput);
  }

  private ConfirmInputResponse confirmInputResponse(RegisteredInput confirmedInput) {
    confirmedInput.getSorobanInput().setSorobanLastSeen();
    String signedBordereau64 = WhirlpoolProtocol.encodeBytes(confirmedInput.getSignedBordereau());
    return new ConfirmInputResponse(signedBordereau64);
  }
}
