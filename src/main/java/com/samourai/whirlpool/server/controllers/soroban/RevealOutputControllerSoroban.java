package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.soroban.protocol.payload.AckResponse;
import com.samourai.wallet.sorobanClient.SorobanPayloadable;
import com.samourai.whirlpool.protocol.SorobanAppWhirlpool;
import com.samourai.whirlpool.protocol.soroban.payload.mix.RevealOutputRequest;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.services.RevealOutputService;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RevealOutputControllerSoroban extends AbstractPerMixControllerSoroban {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private RevealOutputService revealOutputService;

  public RevealOutputControllerSoroban(
      WhirlpoolServerContext serverContext,
      SorobanAppWhirlpool sorobanAppWhirlpool,
      RevealOutputService revealOutputService,
      Mix mix) {
    super(
        serverContext,
        sorobanAppWhirlpool.getEndpointMixRevealOutput(
            mix.getMixId(), serverContext.getCoordinatorWallet().getPaymentCode()),
        mix,
        "REVEAL_OUTPUT");
    this.revealOutputService = revealOutputService;
  }

  @Override
  protected SorobanPayloadable doComputeReplyOnRequestNewForCaching(SorobanItemTyped request)
      throws Exception {
    // update last seen
    RegisteredInput registeredInput = setMixInputLastSeen(request.getMetaSender());

    // reply
    return revealOutput(registeredInput, request.read(RevealOutputRequest.class));
  }

  protected AckResponse revealOutput(RegisteredInput registeredInput, RevealOutputRequest payload)
      throws Exception {
    // reveal output
    revealOutputService.revealOutput(payload.receiveAddress, mix, registeredInput);

    // reply ACK
    return new AckResponse();
  }
}
