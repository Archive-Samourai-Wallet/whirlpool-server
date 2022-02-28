package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.javaserver.exceptions.RestException;
import com.samourai.wallet.api.backend.beans.BackendPushTxException;
import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.PushTxErrorResponse;
import com.samourai.whirlpool.protocol.rest.PushTxSuccessResponse;
import com.samourai.whirlpool.protocol.rest.Tx0PushRequest;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.PushService;
import java.lang.invoke.MethodHandles;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PushController extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;
  private PushService pushService;

  @Autowired
  public PushController(PoolService poolService, PushService pushService) {
    this.poolService = poolService;
    this.pushService = pushService;
  }

  @RequestMapping(value = WhirlpoolEndpoint.REST_TX0_PUSH, method = RequestMethod.POST)
  public PushTxSuccessResponse tx0Push(
      HttpServletRequest request, @RequestBody Tx0PushRequest payload) throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) " + WhirlpoolEndpoint.REST_TX0_PUSH + " " + payload.poolId);
    }
    try {
      Pool pool = poolService.getPool(payload.poolId);

      // validate tx0 & push
      byte[] txBytes = WhirlpoolProtocol.decodeBytes(payload.tx64);
      String txid = pushService.validateAndPushTx0(txBytes, System.currentTimeMillis(), pool);

      // TODO validate & metric
      /*
      // run tx0 analyzis in another thread
      taskService.runOnce(1, () -> {
        try {
          // verify tx0
          RpcTransaction rpcTransaction =
                  blockchainDataService.getRpcTransaction(payload.txid).orElseThrow(() -> notFoundException);
          metricService.onTx0(payload, payload.poolId);
        } catch (Exception e) {
          log.error("tx0Notify failed", e);
        }
      });
      */
      return new PushTxSuccessResponse(txid);
    } catch (BackendPushTxException e) {
      // forward PushTxException as PushTxErrorResponse
      PushTxErrorResponse response =
          new PushTxErrorResponse(e.getMessage(), e.getPushTxError(), e.getVoutsAddressReuse());
      throw new RestException(response);
    }
  }
}
