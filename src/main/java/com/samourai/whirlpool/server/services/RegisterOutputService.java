package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.wallet.api.backend.seenBackend.ISeenBackend;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.server.beans.FailMode;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.MixStatus;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import java.lang.invoke.MethodHandles;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RegisterOutputService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MixService mixService;
  private DbService dbService;
  private FormatsUtilGeneric formatsUtil;
  private CryptoService cryptoService;
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private MessageSignUtilGeneric messageSignUtil;
  private ISeenBackend seenBackend;

  @Autowired
  public RegisterOutputService(
      MixService mixService,
      DbService dbService,
      FormatsUtilGeneric formatsUtil,
      CryptoService cryptoService,
      WhirlpoolServerConfig whirlpoolServerConfig,
      MessageSignUtilGeneric messageSignUtil,
      ISeenBackend seenBackend) {
    this.mixService = mixService;
    this.dbService = dbService;
    this.formatsUtil = formatsUtil;
    this.cryptoService = cryptoService;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.messageSignUtil = messageSignUtil;
    this.seenBackend = seenBackend;
  }

  public synchronized void registerOutput(
      String inputsHash, byte[] unblindedSignedBordereau, String receiveAddress, byte[] bordereau)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) registerOutput: " + inputsHash + " " + receiveAddress);
    }

    try {
      // validate
      validate(receiveAddress);

      // failMode
      whirlpoolServerConfig.checkFailMode(FailMode.REGISTER_OUTPUT);

      // register
      doRegisterOutput(inputsHash, unblindedSignedBordereau, receiveAddress, bordereau);

      // revoke output
      try {
        if (!dbService.hasMixOutput(receiveAddress)) {
          dbService.saveMixOutput(receiveAddress);
        }
      } catch (Exception e) {
        log.error("", e);
      }
    } catch (Exception e) {
      log.info("registerOutput failed for " + receiveAddress + ": " + e.getMessage());
      mixService.registerOutputFailure(inputsHash, receiveAddress);
      throw e;
    }
  }

  private void doRegisterOutput(
      String inputsHash, byte[] unblindedSignedBordereau, String receiveAddress, byte[] bordereau)
      throws Exception {
    Mix mix = mixService.getMixByInputsHash(inputsHash, MixStatus.REGISTER_OUTPUT);

    // verify bordereau not already registered
    if (bordereau == null) {
      throw new IllegalInputException(WhirlpoolErrorCode.INVALID_ARGUMENT, "Invalid bordereau");
    }
    if (mix.hasBordereau(bordereau)) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED, "Bordereau already registered");
    }

    // verify receiveAddress not already registered
    if (StringUtils.isEmpty(receiveAddress)) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INVALID_ARGUMENT, "Invalid receiveAddress");
    }
    if (mix.hasReceiveAddress(receiveAddress)) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED, "receiveAddress already registered");
    }

    // verify unblindedSignedBordereau
    if (!cryptoService.verifyUnblindedSignedBordereau(
        bordereau, unblindedSignedBordereau, mix.getKeyPair())) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INVALID_ARGUMENT, "Invalid unblindedSignedBordereau");
    }

    // verify no output address reuse with inputs
    if (mix.getInputs().findByAddress(receiveAddress).isPresent()) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED, "output already registered as input");
    }

    log.info("[" + mix.getLogId() + "] registered output: " + receiveAddress);
    mix.registerOutput(receiveAddress, bordereau);

    mixService.onRegisterOutput(mix);
  }

  public void checkOutput(String receiveAddress, String signature) throws Exception {
    NetworkParameters params = whirlpoolServerConfig.getNetworkParameters();

    // verify signature
    if (!messageSignUtil.verifySignedMessage(receiveAddress, receiveAddress, signature, params)) {
      throw new NotifiableException(WhirlpoolErrorCode.INVALID_ARGUMENT, "Invalid signature");
    }

    // validate
    try {
      validate(receiveAddress);
    } catch (IllegalInputException e) {
      log.info("checkOutput failed for " + receiveAddress + ": " + e.getMessage());
      throw e;
    }
  }

  private void validate(String receiveAddress) throws Exception {
    // verify output
    if (!formatsUtil.isValidBech32(receiveAddress)) {
      throw new IllegalInputException(WhirlpoolErrorCode.INPUT_REJECTED, "Invalid receiveAddress");
    }

    // verify output not revoked
    /*if (dbService.hasMixOutput(receiveAddress)) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED, "Output already registered");
    }*/

    boolean seen = false;
    try {
      seen = seenBackend.seen(receiveAddress);
    } catch (Exception e) {
      // ignore http failures
      if (log.isDebugEnabled()) {
        log.error("seenBackend failure", e);
      }
    }
    if (seen) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED, "Output address already used");
    }
  }
}
