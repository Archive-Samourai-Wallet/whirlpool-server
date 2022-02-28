package com.samourai.whirlpool.server.controllers.rest;

import com.google.common.collect.ImmutableMap;
import com.samourai.javaserver.exceptions.RestException;
import com.samourai.wallet.api.backend.beans.PushTxAddressReuseException;
import com.samourai.wallet.api.backend.beans.PushTxException;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.*;
import com.samourai.whirlpool.server.beans.Partner;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.*;
import com.samourai.whirlpool.server.utils.Utils;
import com.samourai.xmanager.client.XManagerClient;
import com.samourai.xmanager.protocol.XManagerService;
import com.samourai.xmanager.protocol.rest.AddressIndexResponse;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class Tx0Controller extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;
  private PartnerService partnerService;
  private FeeValidationService feeValidationService;
  private FeePayloadService feePayloadService;
  private ExportService exportService;
  private WhirlpoolServerConfig serverConfig;
  private XManagerClient xManagerClient;
  private BackendService backendService;

  @Autowired
  public Tx0Controller(
      PoolService poolService,
      PartnerService partnerService,
      FeeValidationService feeValidationService,
      FeePayloadService feePayloadService,
      ExportService exportService,
      WhirlpoolServerConfig serverConfig,
      XManagerClient xManagerClient,
      BackendService backendService) {
    this.poolService = poolService;
    this.partnerService = partnerService;
    this.feeValidationService = feeValidationService;
    this.feePayloadService = feePayloadService;
    this.exportService = exportService;
    this.serverConfig = serverConfig;
    this.xManagerClient = xManagerClient;
    this.backendService = backendService;
  }

  @RequestMapping(value = WhirlpoolEndpoint.REST_PREFIX + "tx0Notify", method = RequestMethod.POST)
  public void tx0Notify(HttpServletRequest request, @RequestBody PushTxRequest payload) {
    // ignore old clients
    return;
  }

  @RequestMapping(value = WhirlpoolEndpoint.REST_TX0_PUSH, method = RequestMethod.POST)
  public void tx0Push(HttpServletRequest request, @RequestBody PushTxRequest payload)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) " + WhirlpoolEndpoint.REST_TX0_PUSH);
    }

    // validate tx
    try {
      byte[] txBytes = WhirlpoolProtocol.decodeBytes(payload.tx);
      Transaction tx = new Transaction(serverConfig.getNetworkParameters(), txBytes);
      log.info("tx0Push: " + tx.getHashAsString());
    } catch (Exception e) {
      log.error("", e);
      throw new NotifiableException("Tx parsing error");
    }

    // push
    pushTx(payload.tx);

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
  }

  protected void pushTx(String txHex) throws Exception {
    try {
      backendService.pushTx(txHex);
    } catch (PushTxAddressReuseException e) {
      PushTxErrorResponse response =
          new PushTxErrorResponse(
              e.getMessage(), e.getPushTxError(), e.getAdressReuseOutputIndexs());
      throw new RestException(response);
    } catch (PushTxException e) {
      PushTxErrorResponse response = new PushTxErrorResponse(e.getMessage(), e.getPushTxError());
      throw new RestException(response);
    }
  }

  @RequestMapping(value = WhirlpoolEndpoint.REST_TX0_DATA, method = RequestMethod.POST)
  public Tx0DataResponseV2 tx0Data(
      HttpServletRequest request, @RequestBody Tx0DataRequestV2 tx0DataRequest) throws Exception {

    // prevent bruteforce attacks
    Thread.sleep(700);

    String scode = tx0DataRequest.scode;
    if (StringUtils.isEmpty(scode) || "null".equals(scode)) {
      scode = null;
    }

    String partnerId = tx0DataRequest.partnerId;
    if (StringUtils.isEmpty(partnerId) || "null".equals(partnerId)) {
      partnerId = WhirlpoolProtocol.PARTNER_ID_SAMOURAI;
    }

    Partner partner = partnerService.getById(partnerId); // validate partner

    if (log.isDebugEnabled()) {
      log.debug(
          "Tx0Data: scode=" + (scode != null ? scode : "null") + ", partnerId=" + partner.getId());
    }

    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig =
        feeValidationService.getScodeConfigByScode(scode, System.currentTimeMillis());
    if (scodeConfig == null && scode != null) {
      log.warn("Tx0Data: Invalid SCODE: " + scode);
    }

    List<Tx0DataResponseV2.Tx0Data> tx0Datas = new LinkedList<>();
    for (Pool pool : poolService.getPools()) {
      Tx0DataResponseV2.Tx0Data tx0Data = computeTx0Data(pool.getPoolId(), scodeConfig, partner);
      tx0Datas.add(tx0Data);
    }

    // log activity
    Map<String, String> details =
        ImmutableMap.of("scode", (scode != null ? scode : "null"), "partner", partnerId);
    ActivityCsv activityCsv = new ActivityCsv("TX0", null, null, details, request);
    exportService.exportActivity(activityCsv);

    Tx0DataResponseV2 tx0DataResponse =
        new Tx0DataResponseV2(tx0Datas.toArray(new Tx0DataResponseV2.Tx0Data[] {}));
    return tx0DataResponse;
  }

  private Tx0DataResponseV2.Tx0Data computeTx0Data(
      String poolId, WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig, Partner partner)
      throws Exception {
    short scodePayload;
    long feeValue;
    int feeDiscountPercent;
    String message;

    PoolFee poolFee = poolService.getPool(poolId).getPoolFee();
    if (scodeConfig != null) {
      // scode found => apply discount
      scodePayload = scodeConfig.getPayload();
      feeValue = poolFee.computeFeeValue(scodeConfig.getFeeValuePercent());
      feeDiscountPercent = 100 - scodeConfig.getFeeValuePercent();
      message = scodeConfig.getMessage();
    } else {
      // no SCODE => 100% fee
      scodePayload = FeePayloadService.SCODE_PAYLOAD_NONE;
      feeValue = poolFee.getFeeValue();
      feeDiscountPercent = 0;
      message = null;
    }

    // fetch feeAddress
    int feeIndex;
    String feeAddress;
    long feeChange;
    if (feeValue > 0) {
      // fees
      XManagerService xManagerService = partner.getXmService();
      AddressIndexResponse addressIndexResponse =
          xManagerClient.getAddressIndexOrDefault(xManagerService);
      feeIndex = addressIndexResponse.index;
      feeAddress = addressIndexResponse.address;
      feeChange = 0;
    } else {
      // no fees
      feeIndex = 0;
      feeAddress = null;
      feeChange = computeRandomFeeChange(poolFee);
    }

    byte[] feePayload =
        feePayloadService.encodeFeePayload(feeIndex, scodePayload, partner.getPayload());
    String feePayload64 = WhirlpoolProtocol.encodeBytes(feePayload);

    if (log.isDebugEnabled()) {
      log.debug(
          "Tx0Data["
              + poolId
              + "] feeValuePercent="
              + (scodeConfig != null ? scodeConfig.getFeeValuePercent() + "%" : "null")
              + ", pool.feeValue="
              + poolFee.getFeeValue()
              + ", feeValue="
              + feeValue
              + ", feeChange="
              + feeChange
              + ", feeIndex="
              + feeIndex
              + ", feeAddress="
              + (feeAddress != null ? feeAddress : "")
              + ", partnerId="
              + partner.getId());
    }
    String feePaymentCode = feeValidationService.getFeePaymentCode();
    return new Tx0DataResponseV2.Tx0Data(
        poolId,
        feePaymentCode,
        feeValue,
        feeChange,
        feeDiscountPercent,
        message,
        feePayload64,
        feeAddress);
  }

  @RequestMapping(value = WhirlpoolEndpoint.REST_TX0_DATA, method = RequestMethod.GET)
  public Tx0DataResponseV1 tx0DataV1(
      HttpServletRequest request,
      @RequestParam(value = "poolId", required = true) String poolId,
      @RequestParam(value = "scode", required = false) String scode)
      throws Exception {

    // prevent bruteforce attacks
    Thread.sleep(1000);

    if (StringUtils.isEmpty(scode) || "null".equals(scode)) {
      scode = null;
    }

    PoolFee poolFee = poolService.getPool(poolId).getPoolFee();

    String feePaymentCode = feeValidationService.getFeePaymentCode();
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig =
        feeValidationService.getScodeConfigByScode(scode, System.currentTimeMillis());
    String feePayload64;
    long feeValue;
    int feeDiscountPercent;
    String message;

    if (scodeConfig != null) {
      // scode found => scodeConfig.feeValuePercent
      byte[] feePayload = scodePayloadShortToBytesV1(scodeConfig.getPayload());
      feePayload64 = WhirlpoolProtocol.encodeBytes(feePayload);
      feeValue = poolFee.computeFeeValue(scodeConfig.getFeeValuePercent());
      feeDiscountPercent = 100 - scodeConfig.getFeeValuePercent();
      message = scodeConfig.getMessage();
    } else {
      // no SCODE => 100% fee
      feePayload64 = null;
      feeValue = poolFee.getFeeValue();
      feeDiscountPercent = 0;
      message = null;

      if (scode != null) {
        log.warn("Invalid SCODE: " + scode);
      }
    }

    // fetch feeAddress
    int feeIndex;
    String feeAddress;
    long feeChange;
    if (feeValue > 0) {
      // fees
      AddressIndexResponse addressIndexResponse =
          xManagerClient.getAddressIndexOrDefault(XManagerService.WHIRLPOOL);
      feeIndex = addressIndexResponse.index;
      feeAddress = addressIndexResponse.address;
      feeChange = 0;
    } else {
      // no fees
      feeIndex = 0;
      feeAddress = null;
      feeChange = computeRandomFeeChange(poolFee);
    }

    if (log.isDebugEnabled()) {
      String scodeStr = !StringUtils.isEmpty(scode) ? scode : "null";
      log.debug(
          "Tx0Data: scode="
              + scodeStr
              + ", pool.feeValue="
              + poolFee.getFeeValue()
              + ", feeValue="
              + feeValue
              + ", feeChange="
              + feeChange
              + ", feeIndex="
              + feeIndex
              + ", feeAddress="
              + (feeAddress != null ? feeAddress : ""));
    }

    // log activity
    Map<String, String> details = ImmutableMap.of("scode", (scode != null ? scode : "null"));
    ActivityCsv activityCsv = new ActivityCsv("TX0", poolId, null, details, request);
    exportService.exportActivity(activityCsv);

    Tx0DataResponseV1 tx0DataResponse =
        new Tx0DataResponseV1(
            feePaymentCode,
            feeValue,
            feeChange,
            feeDiscountPercent,
            message,
            feePayload64,
            feeAddress,
            feeIndex);
    return tx0DataResponse;
  }

  public static byte[] scodePayloadShortToBytesV1(short feePayloadAsShort) {
    return ByteBuffer.allocate(2).putShort(feePayloadAsShort).array();
  }

  private long computeRandomFeeChange(PoolFee poolFee) {
    // random SCODE
    List<WhirlpoolServerConfig.ScodeSamouraiFeeConfig> nonZeroScodes =
        serverConfig.getSamouraiFees().getScodes().values().stream()
            .filter(c -> c.getFeeValuePercent() > 0)
            .collect(Collectors.toList());
    if (nonZeroScodes.isEmpty()) {
      // no SCODE available => use 100% pool fee
      long feeValue = poolFee.getFeeValue();
      if (log.isDebugEnabled()) {
        log.debug("changeValue: no scode available => feeValuePercent=100, feeValue=" + feeValue);
      }
      return feeValue;
    }

    // use random SCODE
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig = Utils.getRandomEntry(nonZeroScodes);
    int feeValuePercent = scodeConfig.getFeeValuePercent();
    long feeValue = poolFee.computeFeeValue(feeValuePercent);
    return feeValue;
  }
}
