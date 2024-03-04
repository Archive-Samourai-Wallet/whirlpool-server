package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.soroban.protocol.payload.AckResponse;
import com.samourai.wallet.sorobanClient.SorobanPayloadable;
import com.samourai.whirlpool.protocol.SorobanAppWhirlpool;
import com.samourai.whirlpool.protocol.soroban.payload.mix.SigningRequest;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.services.SigningService;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SigningControllerSoroban extends AbstractPerMixControllerSoroban {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private SigningService signingService;

  public SigningControllerSoroban(
      WhirlpoolServerContext serverContext,
      SorobanAppWhirlpool sorobanAppWhirlpool,
      SigningService signingService,
      Mix mix) {
    super(
        serverContext,
        sorobanAppWhirlpool.getEndpointMixSigning(
            mix.getMixId(), serverContext.getCoordinatorWallet().getPaymentCode()),
        mix,
        "SIGNING");
    this.signingService = signingService;
  }

  @Override
  protected SorobanPayloadable doComputeReply(SorobanItemTyped request) throws Exception {
    // update last seen
    RegisteredInput registeredInput = setMixInputLastSeen(request.getMetaSender());

    // reply
    return signing(registeredInput, request.read(SigningRequest.class));
  }

  protected AckResponse signing(RegisteredInput registeredInput, SigningRequest payload)
      throws Exception {

    if (!mix.isSigned(registeredInput)) { // ignore duplicate requests
      // signing
      signingService.signing(payload.witnesses64, mix, registeredInput);
    }

    // reply ACK
    return new AckResponse();
  }
}
