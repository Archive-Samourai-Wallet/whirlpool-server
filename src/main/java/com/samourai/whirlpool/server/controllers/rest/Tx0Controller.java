package com.samourai.whirlpool.server.controllers.rest;

import com.google.common.collect.ImmutableMap;
import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.Tx0DataResponse;
import com.samourai.whirlpool.protocol.rest.Tx0NotifyRequest;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.*;
import com.samourai.whirlpool.server.utils.Utils;
import com.samourai.xmanager.client.XManagerClient;
import com.samourai.xmanager.protocol.XManagerService;
import com.samourai.xmanager.protocol.rest.AddressIndexResponse;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class Tx0Controller extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;
  private FeeValidationService feeValidationService;
  private ExportService exportService;
  private WhirlpoolServerConfig serverConfig;
  private XManagerClient xManagerClient;
  private BlockchainDataService blockchainDataService;
  private TaskService taskService;
  private MetricService metricService;

  @Autowired
  public Tx0Controller(
      PoolService poolService,
      FeeValidationService feeValidationService,
      ExportService exportService,
      WhirlpoolServerConfig serverConfig,
      XManagerClient xManagerClient,
      BlockchainDataService blockchainDataService,
      TaskService taskService,
      MetricService metricService) {
    this.poolService = poolService;
    this.feeValidationService = feeValidationService;
    this.exportService = exportService;
    this.serverConfig = serverConfig;
    this.xManagerClient = xManagerClient;
    this.blockchainDataService = blockchainDataService;
    this.taskService = taskService;
    this.metricService = metricService;
  }

  @RequestMapping(value = WhirlpoolEndpoint.REST_TX0_NOTIFY, method = RequestMethod.POST)
  public void tx0Notify(HttpServletRequest request, @RequestBody Tx0NotifyRequest payload) {
    if (payload.txid == null || payload.poolId == null) {
      // ignore old clients
      return;
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "(<) " + WhirlpoolEndpoint.REST_TX0_NOTIFY + " [" + payload.poolId + "]" + payload.txid);
    }

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

  @RequestMapping(value = WhirlpoolEndpoint.REST_TX0_DATA, method = RequestMethod.GET)
  public Tx0DataResponse tx0Data(
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
      byte[] feePayload = Utils.feePayloadShortToBytes(scodeConfig.getPayload());
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

    Tx0DataResponse tx0DataResponse =
        new Tx0DataResponse(
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

  private long computeRandomFeeChange(PoolFee poolFee) {
    // random SCODE
    List<WhirlpoolServerConfig.ScodeSamouraiFeeConfig> nonZeroScodes =
        serverConfig
            .getSamouraiFees()
            .getScodes()
            .values()
            .stream()
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
