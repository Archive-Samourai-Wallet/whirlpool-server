package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.SorobanInput;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.BannedInputException;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.persistence.to.BanTO;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RegisterInputService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String HEALTH_CHECK_UTXO = "HEALTH_CHECK";
  public static final String HEALTH_CHECK_SUCCESS = "HEALTH_CHECK_SUCCESS";
  public static final String ERROR_ALREADY_SPENT = "Input already mixed or spent";

  private WhirlpoolServerConfig whirlpoolServerConfig;
  private FormatsUtilGeneric formatsUtil;
  private BlockchainDataService blockchainDataService;
  private InputValidationService inputValidationService;
  private BanService banService;
  private DbService dbService;

  @Autowired
  public RegisterInputService(
      WhirlpoolServerConfig whirlpoolServerConfig,
      FormatsUtilGeneric formatsUtil,
      BlockchainDataService blockchainDataService,
      InputValidationService inputValidationService,
      BanService banService,
      DbService dbService) {
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.formatsUtil = formatsUtil;
    this.blockchainDataService = blockchainDataService;
    this.inputValidationService = inputValidationService;
    this.banService = banService;
    this.dbService = dbService;
  }

  public RegisteredInput validateRegisterInputRequest(
      Pool pool,
      String username,
      String signature,
      String utxoHash,
      long utxoIndex,
      boolean liquidity,
      Boolean tor,
      int blockHeight,
      SorobanInput sorobanInputOrNull)
      throws NotifiableException {

    // check blockHeight
    if (blockHeight > 0) { // check disabled for protocol < 0.23.9
      if (!blockchainDataService.checkBlockHeight(blockHeight)) {
        throw new IllegalInputException(
            WhirlpoolErrorCode.INVALID_BLOCK_HEIGHT, "Invalid blockHeight: " + blockHeight);
      }
    }

    if (HEALTH_CHECK_UTXO.equals(utxoHash)) {
      throw new IllegalInputException(WhirlpoolErrorCode.INPUT_REJECTED, HEALTH_CHECK_SUCCESS);
    }
    if (!formatsUtil.isValidTxHash(utxoHash)) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_REJECTED, "Invalid utxoHash: " + utxoHash);
    }
    if (utxoIndex < 0) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_REJECTED, "Invalid utxoIndex: " + utxoIndex);
    }

    // verify UTXO not banned
    Optional<BanTO> banTO = banService.findActiveBan(utxoHash, utxoIndex);
    if (banTO.isPresent()) {
      log.warn(
          "Rejecting banned UTXO: ["
              + banTO.get()
              + "], tor="
              + BooleanUtils.toStringTrueFalse(tor));
      String banMessage = banTO.get().computeBanMessage();
      throw new BannedInputException(banMessage);
    }

    try {
      // fetch outPoint
      IllegalInputException notFoundException =
          new IllegalInputException(
              WhirlpoolErrorCode.INPUT_REJECTED, "UTXO not found: " + utxoHash + "-" + utxoIndex);
      RpcTransaction rpcTransaction =
          blockchainDataService.getRpcTransaction(utxoHash).orElseThrow(() -> notFoundException);
      TxOutPoint txOutPoint =
          blockchainDataService
              .getOutPoint(rpcTransaction, utxoIndex)
              .orElseThrow(() -> notFoundException);

      // verify signature
      String poolId = pool.getPoolId();
      inputValidationService.validateSignature(txOutPoint, poolId, signature);

      // verify unspent
      if (!blockchainDataService.isTxOutUnspent(utxoHash, utxoIndex)) {
        // spent input being resubmitted by client = spending tx is missing in mempool backing CLI
        // we assume it's a mix tx which was removed from CLI mempool due to mempool congestion
        throw new IllegalInputException(
            WhirlpoolErrorCode.INPUT_REJECTED, RegisterInputService.ERROR_ALREADY_SPENT);
      }

      // check tx0Whitelist
      String txid = rpcTransaction.getTx().getHashAsString();
      if (!dbService.hasTx0Whitelist(txid)) {
        // verify input is a valid mustMix or liquidity
        boolean hasMixTxid = dbService.hasMixTxid(txid, txOutPoint.getValue());
        inputValidationService.validateProvenance(rpcTransaction, liquidity, pool, hasMixTxid);
      } else {
        log.warn("tx0 check disabled by whitelist for txid=" + txid);
      }

      // verify balance
      long inputBalance = txOutPoint.getValue();
      if (!pool.checkInputBalance(inputBalance, liquidity)) {
        long balanceMin = pool.computePremixBalanceMin(liquidity);
        long balanceMax = pool.computePremixBalanceMax(liquidity);
        throw new IllegalInputException(
            WhirlpoolErrorCode.INPUT_REJECTED,
            "Invalid input balance (expected: "
                + balanceMin
                + "-"
                + balanceMax
                + ", actual:"
                + txOutPoint.getValue()
                + ")");
      }
      // verify confirmations
      if (!isUtxoConfirmed(txOutPoint, liquidity)) {
        throw new IllegalInputException(
            WhirlpoolErrorCode.INPUT_REJECTED, "Input is not confirmed");
      }

      // register input to pool
      RegisteredInput registeredInput =
          new RegisteredInput(
              poolId, username, liquidity, txOutPoint, tor, null, sorobanInputOrNull);

      return registeredInput;
    } catch (NotifiableException e) { // validation error or input rejected
      log.warn("Input rejected (" + utxoHash + ":" + utxoIndex + "): " + e.getMessage());
      throw e;
    }
  }

  private boolean isUtxoConfirmed(TxOutPoint txOutPoint, boolean liquidity) {
    int inputConfirmations = txOutPoint.getConfirmations();
    if (liquidity) {
      // liquidity
      int minConfirmationsMix =
          whirlpoolServerConfig.getRegisterInput().getMinConfirmationsLiquidity();
      if (inputConfirmations < minConfirmationsMix) {
        log.info(
            "input not confirmed: liquidity needs at least "
                + minConfirmationsMix
                + " confirmations: "
                + txOutPoint.getHash());
        return false;
      }
    } else {
      // mustMix
      int minConfirmationsTx0 =
          whirlpoolServerConfig.getRegisterInput().getMinConfirmationsMustMix();
      if (inputConfirmations < minConfirmationsTx0) {
        log.info(
            "input not confirmed: mustMix needs at least "
                + minConfirmationsTx0
                + " confirmations: "
                + txOutPoint.getHash());
        return false;
      }
    }
    return true;
  }
}
