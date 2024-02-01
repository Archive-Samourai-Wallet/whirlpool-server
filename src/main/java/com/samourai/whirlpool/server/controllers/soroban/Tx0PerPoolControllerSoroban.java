package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.soroban.client.SorobanPayloadable;
import com.samourai.soroban.client.endpoint.controller.SorobanControllerTypedWithCachedReply;
import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.wallet.api.backend.beans.BackendPushTxException;
import com.samourai.whirlpool.protocol.SorobanAppWhirlpool;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.soroban.payload.beans.PushTxError;
import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0DataRequest;
import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0DataResponse;
import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0PushRequest;
import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0PushResponseError;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.services.Tx0Service;
import java.lang.invoke.MethodHandles;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tx0PerPoolControllerSoroban extends SorobanControllerTypedWithCachedReply {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Tx0Service tx0Service;
  private String poolId;

  public Tx0PerPoolControllerSoroban(
      WhirlpoolServerContext serverContext,
      Tx0Service tx0Service,
      SorobanAppWhirlpool sorobanAppWhirlpool,
      String poolId) {
    super(
        0,
        "TX0[" + poolId + "]",
        serverContext.getRpcSession(),
        sorobanAppWhirlpool.getEndpointTx0(
            serverContext.getCoordinatorWallet().getPaymentCode(), poolId));
    this.tx0Service = tx0Service;
    this.poolId = poolId;
  }

  @PreDestroy
  @Override
  public synchronized void stop() {
    super.stop();
  }

  @Override
  protected SorobanPayloadable computeReplyOnRequestNewForCaching(
      SorobanItemTyped request, String key) throws Exception {
    if (request.isTyped(Tx0DataRequest.class)) {
      return tx0Data(request.read(Tx0DataRequest.class));
    }
    if (request.isTyped(Tx0PushRequest.class)) {
      return tx0Push(request.read(Tx0PushRequest.class));
    }
    throw new Exception("Unexpected request type: " + request.getType());
  }

  protected Tx0DataResponse tx0Data(Tx0DataRequest tx0DataRequest) throws Exception {
    // TODO checkPoolId(tx0PushRequest.poolId);
    return tx0Service.tx0Data(tx0DataRequest, false, null, null);
  }

  protected SorobanPayloadable tx0Push(Tx0PushRequest tx0PushRequest) throws Exception {
    checkPoolId(tx0PushRequest.poolId);
    try {
      return tx0Service.pushTx0(tx0PushRequest);
    } catch (BackendPushTxException e) {
      return new Tx0PushResponseError(
          e.getMessage(), new PushTxError(e.getPushTxError(), e.getVoutsAddressReuse()));
    }
  }

  private void checkPoolId(String requestPoolId) throws Exception {
    if (!poolId.equals(requestPoolId)) {
      throw new NotifiableException(WhirlpoolErrorCode.INVALID_ARGUMENT, "Invalid poolId");
    }
  }
}
