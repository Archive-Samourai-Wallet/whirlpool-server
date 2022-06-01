package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.whirlpool.server.beans.FailMode;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.ServerErrorCode;
import java.lang.invoke.MethodHandles;
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
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private MessageSignUtilGeneric messageSignUtil;

  @Autowired
  public RegisterOutputService(
      MixService mixService,
      DbService dbService,
      FormatsUtilGeneric formatsUtil,
      WhirlpoolServerConfig whirlpoolServerConfig,
      MessageSignUtilGeneric messageSignUtil) {
    this.mixService = mixService;
    this.dbService = dbService;
    this.formatsUtil = formatsUtil;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.messageSignUtil = messageSignUtil;
  }

  public void checkOutput(String receiveAddress, String signature) throws Exception {
    NetworkParameters params = whirlpoolServerConfig.getNetworkParameters();

    // verify signature
    if (!messageSignUtil.verifySignedMessage(receiveAddress, receiveAddress, signature, params)) {
      throw new NotifiableException(ServerErrorCode.INVALID_ARGUMENT, "Invalid signature");
    }

    // validate
    try {
      validate(receiveAddress);
    } catch (IllegalInputException e) {
      log.info("checkOutput failed for " + receiveAddress + ": " + e.getMessage());
      throw e;
    }
  }

  public synchronized Mix registerOutput(
      String inputsHash, byte[] unblindedSignedBordereau, String receiveAddress, byte[] bordereau)
      throws Exception {

    try {
      // validate
      validate(receiveAddress);

      // failMode
      whirlpoolServerConfig.checkFailMode(FailMode.REGISTER_OUTPUT);

      // register
      Mix mix =
          mixService.registerOutput(
              inputsHash, unblindedSignedBordereau, receiveAddress, bordereau);

      // revoke output
      dbService.saveMixOutput(receiveAddress);

      return mix;
    } catch (Exception e) {
      log.info("registerOutput failed for " + receiveAddress + ": " + e.getMessage());
      mixService.registerOutputFailure(inputsHash, receiveAddress);
      throw e;
    }
  }

  private void validate(String receiveAddress) throws Exception {
    // verify output
    if (!formatsUtil.isValidBech32(receiveAddress)) {
      throw new IllegalInputException(ServerErrorCode.INPUT_REJECTED, "Invalid receiveAddress");
    }

    // verify output not revoked
    if (dbService.hasMixOutput(receiveAddress)) {
      throw new IllegalInputException(
          ServerErrorCode.INPUT_ALREADY_REGISTERED, "Output already registered");
    }
  }
}
