package com.samourai.whirlpool.server.services;

import com.google.common.collect.ImmutableMap;
import com.samourai.wallet.api.backend.beans.BackendPushTxException;
import com.samourai.wallet.util.RandomUtil;
import com.samourai.wallet.util.TxUtil;
import com.samourai.wallet.util.Util;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0DataRequest;
import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0DataResponse;
import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0PushRequest;
import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0PushResponseSuccess;
import com.samourai.whirlpool.protocol.v0.rest.Tx0DataResponseV1;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.utils.Utils;
import com.samourai.xmanager.client.XManagerClient;
import com.samourai.xmanager.protocol.XManagerService;
import com.samourai.xmanager.protocol.rest.AddressIndexResponse;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Tx0Service {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;
  private PartnerService partnerService;
  private Tx0ValidationService tx0ValidationService;
  private ScodeService scodeService;
  private FeePayloadService feePayloadService;
  private ExportService exportService;
  private WhirlpoolServerConfig serverConfig;
  private WhirlpoolServerContext serverContext;
  private XManagerClient xManagerClient;
  private BackendService backendService;
  private MetricService metricService;

  @Autowired
  public Tx0Service(
      PoolService poolService,
      PartnerService partnerService,
      Tx0ValidationService tx0ValidationService,
      ScodeService scodeService,
      FeePayloadService feePayloadService,
      ExportService exportService,
      WhirlpoolServerConfig serverConfig,
      WhirlpoolServerContext serverContext,
      XManagerClient xManagerClient,
      BackendService backendService,
      MetricService metricService) {
    this.poolService = poolService;
    this.partnerService = partnerService;
    this.tx0ValidationService = tx0ValidationService;
    this.scodeService = scodeService;
    this.feePayloadService = feePayloadService;
    this.exportService = exportService;
    this.serverConfig = serverConfig;
    this.serverContext = serverContext;
    this.xManagerClient = xManagerClient;
    this.backendService = backendService;
    this.metricService = metricService;
  }

  public Tx0DataResponse tx0Data(
      Tx0DataRequest tx0DataRequest,
      boolean opReturnV0,
      Boolean tor,
      Map<String, String> clientDetails)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) tx0Data (" + tx0DataRequest.partnerId + ")");
    }

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
          "Tx0Data: scode="
              + (scode != null ? Util.maskString(scode, 2) : "null")
              + ", partnerId="
              + partner.getId()
              + ", opReturnV0="
              + opReturnV0);
    }

    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig = null;
    if (tx0DataRequest.cascading) {
      scodeConfig = scodeService.getScodeCascading();
    } else {
      scodeConfig = scodeService.getByScode(scode, System.currentTimeMillis());
    }

    if (scodeConfig == null && scode != null) {
      log.warn("Tx0Data: Invalid SCODE: " + scode);
    }

    List<Tx0DataResponse.Tx0Data> tx0Datas = new LinkedList<>();
    for (Pool pool : poolService.getPools()) {
      Tx0DataResponse.Tx0Data tx0Data =
          computeTx0Data(pool.getPoolId(), scodeConfig, partner, opReturnV0);
      tx0Datas.add(tx0Data);
    }

    // log activity
    Map<String, String> details =
        ImmutableMap.of("scode", (scode != null ? scode : "null"), "partner", partnerId);
    ActivityCsv activityCsv = new ActivityCsv("TX0", null, details, tor, clientDetails);
    exportService.exportActivity(activityCsv);

    Tx0DataResponse tx0DataResponse =
        new Tx0DataResponse(tx0Datas.toArray(new Tx0DataResponse.Tx0Data[] {}));
    return tx0DataResponse;
  }

  private Tx0DataResponse.Tx0Data computeTx0Data(
      String poolId,
      WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig,
      Partner partner,
      boolean opReturnV0)
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
        feePayloadService.computeFeePayload(
            feeIndex, scodePayload, partner.getPayload(), opReturnV0);
    String feePayload64 = WhirlpoolProtocol.encodeBytes(feePayload);

    /*if (log.isDebugEnabled()) {
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
              + (feeAddress != null ? feeAddress : "null")
              + ", partnerId="
              + partner.getId()
              + ", opReturnV0="
              + opReturnV0);
    }*/
    String feePaymentCode = tx0ValidationService.getFeePaymentCode(opReturnV0);
    String feeOutputSignature = null;
    if (feeAddress != null) {
      TxOutSignature txOutSignature = computeFeeOutputSignature(feeAddress, feeValue);
      feeOutputSignature = txOutSignature.signature;
    }
    return new Tx0DataResponse.Tx0Data(
        poolId,
        feePaymentCode,
        feeValue,
        feeChange,
        feeDiscountPercent,
        message,
        feePayload64,
        feeAddress,
        feeOutputSignature);
  }

  private TxOutSignature computeFeeOutputSignature(String feeAddress, long feeValue)
      throws Exception {
    TxOutSignature txOutSignature =
        Utils.signTransactionOutput(
            feeAddress,
            feeValue,
            serverConfig.getNetworkParameters(),
            serverContext.getSigningWallet());
    if (log.isDebugEnabled()) {
      log.debug("feeAddress=" + feeAddress + ", feeValue=" + feeValue + ", " + txOutSignature);
    }
    return txOutSignature;
  }

  // OpReturnImplV0 as Tx0DataResponseV1
  @Deprecated
  public Tx0DataResponseV1 tx0DataV1(HttpServletRequest request, String poolId, String scode)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) tx0DataV1 (" + poolId + ")");
    }
    if (StringUtils.isEmpty(scode) || "null".equals(scode)) {
      scode = null;
    }

    PoolFee poolFee = poolService.getPool(poolId).getPoolFee();

    String feePaymentCode = tx0ValidationService.getFeePaymentCode(true);
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig =
        scodeService.getByScode(scode, System.currentTimeMillis());
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
          "Tx0DataV1: scode="
              + Util.maskString(scodeStr, 2)
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
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig =
        RandomUtil.getInstance().next(nonZeroScodes);
    int feeValuePercent = scodeConfig.getFeeValuePercent();
    long feeValue = poolFee.computeFeeValue(feeValuePercent);
    return feeValue;
  }

  public Tx0PushResponseSuccess pushTx0(Tx0PushRequest tx0PushRequest) throws Exception {
    return pushTx0(tx0PushRequest, System.currentTimeMillis());
  }

  protected Tx0PushResponseSuccess pushTx0(Tx0PushRequest tx0PushRequest, long txTime)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) pushTx0 (" + tx0PushRequest.poolId + ")");
    }

    Pool pool = poolService.getPool(tx0PushRequest.poolId);

    // validate tx0
    byte[] txBytes = WhirlpoolProtocol.decodeBytes(tx0PushRequest.tx64);
    Tx0Validation tx0Validation;
    try {
      tx0Validation = tx0ValidationService.parseAndValidate(txBytes, txTime, pool);
    } catch (Exception e) {
      log.error("Not a TX0", e);
      // hide error details and wrap "Not a TX0" as BackendPushTxException
      throw new BackendPushTxException("Not a TX0");
    }

    Transaction tx = tx0Validation.getTx();
    String txid = tx.getHashAsString();
    if (log.isDebugEnabled()) {
      log.info("pushing tx0: " + txid);
    }

    // push with strict mode
    String txHex = TxUtil.getInstance().getTxHex(tx);
    Collection<Integer> strictModeVouts = tx0Validation.findStrictModeVouts();
    backendService.pushTx(txHex, strictModeVouts); // throws BackendPushTxException
    if (log.isDebugEnabled()) {
      log.debug("pushTx0 success: " + txid);
    }

    // metric
    try {
      // TX0 minerFee not available here: tx.getFee() is null because input values are unknown
      Collection<TransactionOutput> premixOutputs = tx0Validation.findPremixOutputs(pool);
      int premixCount = premixOutputs.size();
      long premixVolume = premixOutputs.stream().mapToLong(o -> o.getValue().value).sum();
      int opReturnVersion = tx0Validation.getFeeData().getOpReturnVersion();
      int feePayloadVersion = tx0Validation.getFeeData().getFeePayloadVersion();
      String partner = tx0Validation.getPartner().getId();
      int scodeDiscountPercent = 0;
      WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig = tx0Validation.getScodeConfig();
      if (scodeConfig != null) {
        scodeDiscountPercent = 100 - scodeConfig.getFeeValuePercent();
      }
      if (log.isDebugEnabled()) {
        log.debug(
            "Tx0: "
                + txid
                + ": "
                + premixCount
                + " premixs, partner="
                + partner
                + ", scodeDiscountPercent="
                + scodeDiscountPercent
                + ", opReturnVersion="
                + opReturnVersion
                + ", feePayloadVersion="
                + feePayloadVersion);
      }
      metricService.onTx0(
          pool.getPoolId(),
          premixCount,
          premixVolume,
          opReturnVersion,
          feePayloadVersion,
          scodeDiscountPercent,
          partner);
    } catch (Exception e) {
      log.error("", e);
    }

    return new Tx0PushResponseSuccess(txid);
  }
}
