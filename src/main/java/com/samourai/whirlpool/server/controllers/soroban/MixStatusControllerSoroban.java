package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.wallet.sorobanClient.SorobanPayloadable;
import com.samourai.whirlpool.protocol.SorobanAppWhirlpool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MixStatusControllerSoroban extends AbstractPerMixControllerSoroban {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public MixStatusControllerSoroban(
      WhirlpoolServerContext serverContext, SorobanAppWhirlpool sorobanAppWhirlpool, Mix mix) {
    super(
        serverContext,
        sorobanAppWhirlpool.getEndpointMixStatus(
            mix.getMixId(), serverContext.getCoordinatorWallet().getPaymentCode()),
        mix,
        "MIX_STATUS");
  }

  @Override
  protected SorobanPayloadable doComputeReply(SorobanItemTyped request) throws Exception {
    // update last seen
    RegisteredInput registeredInput = setMixInputLastSeen(request.getMetaSender());

    // reply
    return mixStatusResponse(registeredInput);
  }

  protected SorobanPayloadable mixStatusResponse(RegisteredInput registeredInput) throws Exception {
    // send mixStatusResponse
    return mix.getMixStatusResponse(registeredInput.getSorobanInput());
  }
}
