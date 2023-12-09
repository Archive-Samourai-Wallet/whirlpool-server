package com.samourai.whirlpool.server.services;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.MixStatus;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SigningService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MixService mixService;
  private TxUtil txUtil;

  @Autowired
  public SigningService(MixService mixService, TxUtil txUtil) {
    this.mixService = mixService;
    this.txUtil = txUtil;
  }

  public void signing_webSocket(String mixId, String[] witness64, String username)
      throws Exception {
    // find confirmed input
    Mix mix = mixService.getMix(mixId, MixStatus.SIGNING);
    RegisteredInput confirmedInput =
        mix.getInputs()
            .findByUsername(username)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        WhirlpoolErrorCode.INPUT_REJECTED,
                        "Input not found for signing username=" + username));

    // signing
    signing(witness64, mix, confirmedInput);
  }

  public void signing(String mixId, String[] witness64, PaymentCode sender) throws Exception {
    // find confirmed input
    Mix mix = mixService.getMix(mixId, MixStatus.SIGNING);
    RegisteredInput confirmedInput =
        mix.getInputs()
            .findBySorobanSender(sender)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        WhirlpoolErrorCode.INPUT_REJECTED,
                        "Input not found for signing sender=" + sender.toString()));

    // signing
    signing(witness64, mix, confirmedInput);
  }

  protected synchronized void signing(String[] witness60, Mix mix, RegisteredInput confirmedInput)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) [" + mix.getMixId() + "] signing: " + confirmedInput);
    }

    // check user
    if (mix.isSigned(confirmedInput)) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED, "User already signed");
    }
    TxOutPoint txOutPoint = confirmedInput.getOutPoint();

    // sign
    Transaction tx = mix.getTx();
    Integer inputIndex = txUtil.findInputIndex(tx, txOutPoint.getHash(), txOutPoint.getIndex());
    TransactionWitness witness = Utils.witnessUnserialize64(witness60);
    tx.setWitness(inputIndex, witness);

    // verify
    try {
      txUtil.verifySignInput(tx, inputIndex, txOutPoint.getValue(), txOutPoint.getScriptBytes());
    } catch (Exception e) {
      log.error("Invalid signature: verifySignInput failed", e);
      throw new IllegalInputException(WhirlpoolErrorCode.INVALID_ARGUMENT, "Invalid signature");
    }

    // signature success
    mix.setTx(tx);
    mix.setSigned(confirmedInput);
    log.info("[" + mix.getLogId() + "] signing success: " + confirmedInput);

    mixService.onSign(mix);
  }
}
