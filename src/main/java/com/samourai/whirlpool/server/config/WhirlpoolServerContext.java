package com.samourai.whirlpool.server.config;

import com.samourai.whirlpool.server.beans.SecretWalletContext;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolServerContext {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private SecretWalletContext coordinatorWallet;
  private SecretWalletContext signingWallet;
  private String coordinatorWalletPaymentCodeSignature;

  public WhirlpoolServerContext(WhirlpoolServerConfig serverConfig) throws Exception {
    NetworkParameters params = serverConfig.getNetworkParameters();
    coordinatorWallet = new SecretWalletContext(serverConfig.getCoordinatorWallet(), params);
    signingWallet = new SecretWalletContext(serverConfig.getSigningWallet(), params);
    coordinatorWalletPaymentCodeSignature =
        Utils.signMessage(signingWallet, coordinatorWallet.getPaymentCode().toString());

    log.info("coordinatorWallet: " + coordinatorWallet);
    log.info("signingWallet: " + signingWallet);
  }

  public SecretWalletContext getCoordinatorWallet() {
    return coordinatorWallet;
  }

  public SecretWalletContext getSigningWallet() {
    return signingWallet;
  }

  public String getCoordinatorWalletPaymentCodeSignature() {
    return coordinatorWalletPaymentCodeSignature;
  }
}
