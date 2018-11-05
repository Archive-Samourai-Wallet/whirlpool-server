package com.samourai.whirlpool.server.services;

import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import java.lang.invoke.MethodHandles;
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
  private WhirlpoolServerConfig serverConfig;

  @Autowired
  public RegisterOutputService(
      MixService mixService,
      DbService dbService,
      FormatsUtilGeneric formatsUtil,
      WhirlpoolServerConfig serverConfig) {
    this.mixService = mixService;
    this.dbService = dbService;
    this.formatsUtil = formatsUtil;
    this.serverConfig = serverConfig;
  }

  public void registerOutput(
      String inputsHash, byte[] unblindedSignedBordereau, String receiveAddress) throws Exception {
    // validate
    validate(unblindedSignedBordereau, receiveAddress);

    // register
    mixService.registerOutput(inputsHash, unblindedSignedBordereau, receiveAddress);

    // revoke receiveAddress
    dbService.revokeReceiveAddress(receiveAddress);
  }

  private void validate(byte[] unblindedSignedBordereau, String receiveAddress) throws Exception {
    // verify bordereau
    if (log.isDebugEnabled()) {
      log.debug(
          "Verifying unblindedSignedBordereau for receiveAddress: "
              + receiveAddress
              + " : "
              + WhirlpoolProtocol.encodeBytes(unblindedSignedBordereau));
    }

    // verify output
    if (!formatsUtil.isValidBech32(receiveAddress)) {
      throw new IllegalInputException("Invalid receiveAddress");
    }

    // verify receiveAddress not revoked
    if (dbService.isRevokedReceiveAddress(receiveAddress)) {
      throw new IllegalInputException("receiveAddress already registered: " + receiveAddress);
    }
  }
}
