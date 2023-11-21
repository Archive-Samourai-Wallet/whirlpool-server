package com.samourai.whirlpool.server.services.rpc;

import com.samourai.soroban.client.rpc.RpcClientService;
import com.samourai.wallet.crypto.CryptoUtil;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.JavaHttpClientService;
import org.springframework.stereotype.Service;

@Service
public class RpcClientServiceServer extends RpcClientService {
  public RpcClientServiceServer(
      JavaHttpClientService httpClientService,
      CryptoUtil cryptoUtil,
      WhirlpoolServerConfig serverConfig) {
    super(httpClientService, cryptoUtil, false, serverConfig.getNetworkParameters());
  }
}
