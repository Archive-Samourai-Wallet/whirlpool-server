package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.dex.config.DexConfigResponse;
import com.samourai.dex.config.SamouraiConfig;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactoryGeneric;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.lang.invoke.MethodHandles;
import javax.servlet.http.HttpServletRequest;
import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DexConfigController extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT_DEXCONFIG = "/rest/dex-config";
  private static HD_WalletFactoryGeneric hdWalletFactory = HD_WalletFactoryGeneric.getInstance();

  private WhirlpoolServerConfig serverConfig;
  private DexConfigResponse dexConfigResponse;

  @Autowired
  public DexConfigController(WhirlpoolServerConfig serverConfig) throws Exception {
    this.serverConfig = serverConfig;

    dexConfigResponse = new DexConfigResponse(new SamouraiConfig(), getSigningKey());
  }

  @RequestMapping(value = ENDPOINT_DEXCONFIG, method = RequestMethod.GET)
  public DexConfigResponse dexConfig(HttpServletRequest request) throws Exception {
    return dexConfigResponse;
  }

  private ECKey getSigningKey() throws Exception {
    WhirlpoolServerConfig.SecretWalletConfig secretWalletConfig = serverConfig.getDexConfigWallet();
    HD_Wallet bip44wallet =
        hdWalletFactory.restoreWallet(
            secretWalletConfig.getWords(),
            secretWalletConfig.getPassphrase(),
            serverConfig.getNetworkParameters());
    HD_Address signingAddress = bip44wallet.getAddressAt(0, 0, 0);
    String signingAddressStr = signingAddress.getAddressStringSegwitNative();
    log.info("### DexConfig signingAddress = " + signingAddressStr);
    if (!signingAddressStr.equals(DexConfigResponse.SIGNING_ADDRESS)) {
      throw new Exception(
          "DexConfig signingAddress mismatch: "
              + signingAddressStr
              + " vs "
              + DexConfigResponse.SIGNING_ADDRESS);
    }

    return signingAddress.getECKey();
  }
}
