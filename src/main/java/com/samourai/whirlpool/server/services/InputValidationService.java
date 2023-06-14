package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.beans.Tx0Validation;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.ServerErrorCode;
import com.samourai.whirlpool.server.services.fee.WhirlpoolFeeData;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.stream.Collectors;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InputValidationService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private Tx0ValidationService tx0ValidationService;
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private CryptoService cryptoService;
  private MessageSignUtilGeneric messageSignUtil;
  private BlockchainDataService blockchainDataService;
  private PoolService poolService;

  public InputValidationService(
      Tx0ValidationService tx0ValidationService,
      WhirlpoolServerConfig whirlpoolServerConfig,
      CryptoService cryptoService,
      MessageSignUtilGeneric messageSignUtil,
      BlockchainDataService blockchainDataService,
      PoolService poolService) {
    this.tx0ValidationService = tx0ValidationService;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.cryptoService = cryptoService;
    this.messageSignUtil = messageSignUtil;
    this.blockchainDataService = blockchainDataService;
    this.poolService = poolService;
  }

  public void validateProvenance(
      RpcTransaction tx, boolean liquidity, Pool pool, boolean hasMixTxid)
      throws NotifiableException {

    // provenance verification can be disabled with testMode
    if (whirlpoolServerConfig.isTestMode()) {
      log.warn("tx0 check disabled by testMode");
      return; // valid
    }

    // verify input comes from a valid tx0 or previous mix
    boolean isLiquidity =
        checkInputProvenance(tx.getTx(), tx.getTxTime(), pool.getPoolFee(), hasMixTxid);
    if (!isLiquidity && liquidity) {
      throw new IllegalInputException(
          ServerErrorCode.INPUT_REJECTED, "Input rejected: joined as liquidity but is a mustMix");
    }
    if (isLiquidity && !liquidity) {
      throw new IllegalInputException(
          ServerErrorCode.INPUT_REJECTED,
          "Input rejected: joined as mustMix but is as a liquidity");
    }
    return; // valid
  }

  protected boolean checkInputProvenance(
      Transaction tx, long txTime, PoolFee poolFee, boolean hasMixTxid) throws NotifiableException {
    // is it a tx0?
    WhirlpoolFeeData feeData;
    try {
      feeData = tx0ValidationService.decodeFeeData(tx);
    } catch (Exception e) {
      // this is not a tx0 => liquidity coming from a previous whirlpool tx
      if (log.isTraceEnabled()) {
        log.trace("Validating input: txid=" + tx.getHashAsString() + ": feeData=null");
      }

      if (!hasMixTxid) { // not a whirlpool tx
        log.error("Input rejected (not a premix or whirlpool input)", e);
        throw new IllegalInputException(
            ServerErrorCode.INPUT_REJECTED, "Input rejected (not a premix or whirlpool input)");
      }
      return true; // liquidity
    }

    // this is a tx0 => mustMix
    if (log.isTraceEnabled()) {
      log.trace("Validating input: txid=" + tx.getHashAsString() + ", feeData={" + feeData + "}");
    }

    Tx0Validation tx0Validation;
    try {
      // verify tx0
      tx0Validation = tx0ValidationService.validate(tx, txTime, poolFee, feeData);
    } catch (Exception e) {
      // invalid fees
      log.error(
          "Input rejected (invalid fee for tx0="
              + tx.getHashAsString()
              + ", x="
              + feeData.getFeeIndice()
              + ", feeData={"
              + feeData
              + "})");
      throw new IllegalInputException(
          ServerErrorCode.INPUT_REJECTED,
          "Input rejected (invalid fee for tx0="
              + tx.getHashAsString()
              + ", x="
              + feeData.getFeeIndice()
              + ", scodePayload="
              + (feeData.getScodePayload() != FeePayloadService.SCODE_PAYLOAD_NONE ? "yes" : "no")
              + ")");
    }

    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig = tx0Validation.getScodeConfig();
    if (scodeConfig != null && scodeConfig.isCascading()) {
      // check tx0 cascading
      try {
        validateTx0Cascading(tx);
      } catch (Exception e) {
        log.error("Input rejected (invalid cascading for tx0=" + tx.getHashAsString() + ")", e);
        throw new IllegalInputException(
            ServerErrorCode.INPUT_REJECTED,
            "Input rejected (invalid cascading for tx0=" + tx.getHashAsString() + ")");
      }
    }
    return false; // mustMix
  }

  protected void validateTx0Cascading(Transaction tx) throws Exception {
    // cascading tx0 should only have 1 txid predecessor for previous tx0 change(s)
    Collection<String> prevTxIds =
        tx.getInputs().stream()
            .map(txInput -> txInput.getOutpoint().getHash().toString())
            .distinct()
            .collect(Collectors.toList());
    if (prevTxIds.size() != 1) {
      throw new Exception(
          "Invalid prevTxIds.size for cascading tx0=" + tx.getHashAsString() + "): " + prevTxIds);
    }

    // check if parent tx is valid tx0
    String parentTxId = prevTxIds.iterator().next();

    // get rpc tx of parent tx
    RpcTransaction parentRpcTx = blockchainDataService.getRpcTransaction(parentTxId).get();
    int FIRST_MUSTMIX_OUTPUT_INDEX = 3; // TODO assumes 4th output is utxo to be mixed
    if (parentRpcTx.getTx().getOutputs().size() < (FIRST_MUSTMIX_OUTPUT_INDEX + 1)) {
      throw new Exception("Invalid outputs.size for parentTx=" + parentTxId + ")");
    }

    // get pool of parent tx
    long mustMixValue =
        parentRpcTx.getTx().getOutputs().get(FIRST_MUSTMIX_OUTPUT_INDEX).getValue().getValue();
    Pool parentTxPool =
        poolService
            .findByInputValue(mustMixValue, false)
            .orElseThrow(() -> new Exception("No pool found for parentTx=" + parentTxId + ")"));

    // validate parent tx0
    if (!isValidTx0(parentRpcTx, parentTxPool)) {
      throw new Exception("Not a valid tx0 for parentTx=" + parentTxId);
    }
  }

  protected boolean isValidTx0(RpcTransaction tx, Pool pool) throws Exception {
    boolean hasMixTxid = false; // it's not a MIX tx
    boolean isLiquidity =
        checkInputProvenance(tx.getTx(), tx.getTxTime(), pool.getPoolFee(), hasMixTxid);
    return !isLiquidity; // not a MIX
  }

  public ECKey validateSignature(TxOutPoint txOutPoint, String message, String signature)
      throws IllegalInputException {
    if (log.isTraceEnabled()) {
      log.trace(
          "Verifying signature: "
              + signature
              + "\n  for address: "
              + txOutPoint.getToAddress()
              + "\n  for message: "
              + message);
    }

    // verify signature of message for address
    if (!messageSignUtil.verifySignedMessage(
        txOutPoint.getToAddress(), message, signature, cryptoService.getNetworkParameters())) {
      log.warn(
          "Invalid signature: verifySignedMessage() failed for input="
              + txOutPoint.toKey()
              + ", message="
              + message
              + ", signature="
              + signature
              + ", address="
              + txOutPoint.getToAddress());
      throw new IllegalInputException(ServerErrorCode.INVALID_ARGUMENT, "Invalid signature");
    }

    ECKey pubkey = messageSignUtil.signedMessageToKey(message, signature);
    if (pubkey == null) {
      log.warn(
          "Invalid signature: signedMessageToKey() failed for input="
              + txOutPoint.toKey()
              + ", message="
              + message
              + ", signature="
              + signature);
      throw new IllegalInputException(ServerErrorCode.INVALID_ARGUMENT, "Invalid signature");
    }
    return pubkey;
  }
}
