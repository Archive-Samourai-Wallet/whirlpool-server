package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.soroban.client.AbstractSorobanPayload;
import com.samourai.soroban.client.PayloadWithSender;
import com.samourai.soroban.client.UntypedPayloadWithSender;
import com.samourai.wallet.api.backend.beans.BackendPushTxException;
import com.samourai.whirlpool.protocol.soroban.PushTxErrorResponse;
import com.samourai.whirlpool.protocol.soroban.api.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.protocol.soroban.tx0.Tx0DataRequest;
import com.samourai.whirlpool.protocol.soroban.tx0.Tx0DataResponse;
import com.samourai.whirlpool.protocol.soroban.tx0.Tx0PushRequest;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.services.Tx0Service;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Tx0ControllerSoroban extends AbstractControllerSoroban<UntypedPayloadWithSender> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Tx0Service tx0Service;

  public Tx0ControllerSoroban(
      WhirlpoolServerContext serverContext,
      Tx0Service tx0Service,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator) {
    super(LOOP_DELAY_FAST, "TX0", serverContext, whirlpoolApiCoordinator);
    this.tx0Service = tx0Service;
  }

  @PreDestroy
  @Override
  public synchronized void stop() {
    super.stop();
  }

  @Override
  protected Collection<UntypedPayloadWithSender> fetch() throws Exception {
    return asyncUtil
        .blockingGet(whirlpoolApiCoordinator.fetchTx0Requests())
        .distinctBySender()
        .getList();
  }

  @Override
  protected String computeKey(UntypedPayloadWithSender message) {
    return message.computeUniqueId();
  }

  @Override
  protected void process(UntypedPayloadWithSender message, String key) {
    try {
      message.readOnWithSender(Tx0DataRequest.class, tx0DataRequest -> tx0Data(tx0DataRequest));
      message.readOnWithSender(Tx0PushRequest.class, tx0PushRequest -> pushTx0(tx0PushRequest));
    } catch (Exception e) {
      log.error("", e);
    }
  }

  protected Tx0DataResponse tx0Data(PayloadWithSender<Tx0DataRequest> request) throws Exception {
    Tx0DataResponse response = tx0Service.tx0Data(request.getPayload(), false, null, null);
    sendReplyToRequest(request, response);
    return response;
  }

  protected AbstractSorobanPayload pushTx0(PayloadWithSender<Tx0PushRequest> request)
      throws Exception {
    AbstractSorobanPayload response;
    try {
      response = tx0Service.pushTx0(request.getPayload());
    } catch (BackendPushTxException e) {
      response =
          new PushTxErrorResponse(e.getMessage(), e.getPushTxError(), e.getVoutsAddressReuse());
    }
    sendReplyToRequest(request, response);
    return response;
  }
}
