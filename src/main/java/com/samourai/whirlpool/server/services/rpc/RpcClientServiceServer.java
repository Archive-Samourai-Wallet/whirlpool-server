package com.samourai.whirlpool.server.services.rpc;

import com.samourai.soroban.client.rpc.RpcClientService;
import com.samourai.wallet.bip47.BIP47UtilGeneric;
import com.samourai.wallet.crypto.CryptoUtil;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.JavaHttpClientService;
import org.springframework.stereotype.Service;

@Service
public class RpcClientServiceServer extends RpcClientService {
  public RpcClientServiceServer(
      JavaHttpClientService httpClientService,
      CryptoUtil cryptoUtil,
      BIP47UtilGeneric bip47Util,
      WhirlpoolServerConfig serverConfig) {
    super(httpClientService, cryptoUtil, bip47Util, false, serverConfig.getNetworkParameters());
  }
}
