package com.samourai.whirlpool.server.services;

import com.samourai.wallet.api.backend.BackendApi;
import com.samourai.wallet.api.backend.BackendServer;
import com.samourai.wallet.httpClient.HttpUsage;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import org.springframework.stereotype.Service;

@Service
public class BackendService extends BackendApi {

  public BackendService(
      JavaHttpClientService httpClientService, WhirlpoolServerConfig serverConfig) {
    super(
        httpClientService.getHttpClient(HttpUsage.BACKEND),
        serverConfig.isTestnet()
            ? BackendServer.TESTNET.getBackendUrlClear()
            : BackendServer.MAINNET.getBackendUrlClear(),
        null);
  }
}
