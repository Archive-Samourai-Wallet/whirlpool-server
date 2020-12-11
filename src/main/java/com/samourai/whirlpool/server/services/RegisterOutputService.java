package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
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
      throw new NotifiableException("Invalid signature");
    }

    // validate
    validate(receiveAddress);
  }

  public synchronized Mix registerOutput(
      String inputsHash, byte[] unblindedSignedBordereau, String receiveAddress) throws Exception {
    // validate
    validate(receiveAddress);

    // register
    Mix mix = mixService.registerOutput(inputsHash, unblindedSignedBordereau, receiveAddress);

    // revoke output
    dbService.saveMixOutput(receiveAddress);
    return mix;
  }

  private void validate(String receiveAddress) throws Exception {
    // verify output
    if (!formatsUtil.isValidBech32(receiveAddress)) {
      throw new IllegalInputException("Invalid receiveAddress");
    }

    // verify output not revoked
    if (dbService.hasMixOutput(receiveAddress)) {
      throw new IllegalInputException("Output already registered");
    }
  }
}
