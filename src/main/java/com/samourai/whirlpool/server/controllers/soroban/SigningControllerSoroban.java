package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.soroban.protocol.payload.AckResponse;
import com.samourai.wallet.sorobanClient.SorobanPayloadable;
import com.samourai.whirlpool.protocol.SorobanAppWhirlpool;
import com.samourai.whirlpool.protocol.soroban.payload.mix.SigningRequest;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.services.MetricService;
import com.samourai.whirlpool.server.services.MixService;
import com.samourai.whirlpool.server.services.SigningService;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SigningControllerSoroban extends AbstractPerMixControllerSoroban {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private SigningService signingService;
  private MixService mixService;
  private MetricService metricService;

  public SigningControllerSoroban(
      WhirlpoolServerContext serverContext,
      SorobanAppWhirlpool sorobanAppWhirlpool,
      SigningService signingService,
      MixService mixService,
      MetricService metricService,
      Mix mix) {
    super(
        serverContext,
        sorobanAppWhirlpool.getEndpointMixSigning(
            mix.getMixId(), serverContext.getCoordinatorWallet().getPaymentCode()),
        mix,
        "SIGNING");
    this.signingService = signingService;
    this.mixService = mixService;
    this.metricService = metricService;
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
      Long mixStepElapsedTime = mixService.getMixStepElapsedTime(mix);
      Long mixStepRemainingTime = mixService.getMixStepRemainingTime(mix);

      // signing
      signingService.signing(payload.witnesses64, mix, registeredInput);

      metricService.onClientSigning(mix, mixStepElapsedTime, mixStepRemainingTime, true);
    }

    // reply ACK
    return new AckResponse();
  }
}
