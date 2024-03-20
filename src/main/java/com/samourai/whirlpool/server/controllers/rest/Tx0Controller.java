package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.javaserver.exceptions.RestException;
import com.samourai.wallet.api.backend.beans.BackendPushTxException;
import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0DataRequest;
import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0DataResponse;
import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0PushResponseSuccess;
import com.samourai.whirlpool.protocol.v0.WhirlpoolEndpointV0;
import com.samourai.whirlpool.protocol.v0.rest.*;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.services.Tx0Service;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class Tx0Controller extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Tx0Service tx0Service;

  @Autowired
  public Tx0Controller(Tx0Service tx0Service) {
    this.tx0Service = tx0Service;
  }

  @Deprecated
  @RequestMapping(
      value = WhirlpoolEndpointV0.REST_PREFIX + "tx0Notify",
      method = RequestMethod.POST)
  public void tx0Notify(HttpServletRequest request) {
    // ignore old clients
    return;
  }

  // opReturnV1 as Tx0DataResponseV2
  @RequestMapping(value = WhirlpoolEndpointV0.REST_TX0_DATA_V1, method = RequestMethod.POST)
  public Tx0DataResponseV2 tx0Data(
      HttpServletRequest request, @RequestBody Tx0DataRequestV2 tx0DataRequest) throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) TX0_DATA CLASSIC");
    }
    return doTx0Data(request, tx0DataRequest, false);
  }

  // opReturnV0 as Tx0DataResponseV2
  @RequestMapping(value = WhirlpoolEndpointV0.REST_TX0_DATA_V0, method = RequestMethod.POST)
  public Tx0DataResponseV2 tx0Data_opReturnV0(
      HttpServletRequest request, @RequestBody Tx0DataRequestV2 tx0DataRequest) throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) TX0_DATA CLASSIC");
    }
    return doTx0Data(request, tx0DataRequest, true);
  }

  private Tx0DataResponseV2 doTx0Data(
      HttpServletRequest request, Tx0DataRequestV2 req, boolean opReturnV0) throws Exception {
    // prevent bruteforce attacks
    Thread.sleep(700);

    Boolean tor = Utils.getTor(request);
    Map<String, String> clientDetails = ActivityCsv.computeClientDetails(request);

    Tx0DataRequest tx0DataRequest = new Tx0DataRequest(req.scode, req.partnerId);
    Tx0DataResponse tx0DataResponse =
        tx0Service.tx0Data(tx0DataRequest, opReturnV0, tor, clientDetails);
    return new Tx0DataResponseV2(
        Arrays.stream(tx0DataResponse.tx0Datas)
            .map(
                td ->
                    new Tx0DataResponseV2.Tx0Data(
                        td.poolId,
                        td.feePaymentCode,
                        td.feeValue,
                        td.feeChange,
                        td.feeDiscountPercent,
                        td.message,
                        td.feePayload64,
                        td.feeAddress,
                        td.feeOutputSignature))
            .toArray(i -> new Tx0DataResponseV2.Tx0Data[i]));
  }

  // OpReturnImplV0 as Tx0DataResponseV1
  @RequestMapping(value = WhirlpoolEndpointV0.REST_TX0_DATA_V0, method = RequestMethod.GET)
  public Tx0DataResponseV1 tx0DataV1(
      HttpServletRequest request,
      @RequestParam(value = "poolId", required = true) String poolId,
      @RequestParam(value = "scode", required = false) String scode)
      throws Exception {
    // prevent bruteforce attacks
    Thread.sleep(700);

    if (log.isDebugEnabled()) {
      log.debug("(<) TX0_DATA_V1 CLASSIC " + poolId);
    }
    return tx0Service.tx0DataV1(request, poolId, scode);
  }

  @RequestMapping(value = WhirlpoolEndpointV0.REST_TX0_PUSH, method = RequestMethod.POST)
  public PushTxSuccessResponse tx0Push(@RequestBody Tx0PushRequest request) throws Exception {
    try {
      if (log.isDebugEnabled()) {
        log.debug("(<) TX0_PUSH CLASSIC " + request.poolId);
      }
      com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0PushRequest pushRequest =
          new com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0PushRequest(
              request.tx64, request.poolId);
      Tx0PushResponseSuccess response = tx0Service.pushTx0(pushRequest, false);
      return new PushTxSuccessResponse(response.txid);
    } catch (BackendPushTxException e) {
      // forward PushTxException as PushTxErrorResponse
      PushTxErrorResponse response =
          new PushTxErrorResponse(e.getMessage(), e.getPushTxError(), e.getVoutsAddressReuse());
      throw new RestException(response);
    }
  }
}
