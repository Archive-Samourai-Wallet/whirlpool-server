package com.samourai.whirlpool.server.services.rpc;

import com.samourai.soroban.client.rpc.RpcClientService;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.JavaHttpClientService;
import org.springframework.stereotype.Service;

@Service
public class RpcClientServiceServer extends RpcClientService {
  public RpcClientServiceServer(
      JavaHttpClientService httpClientService, WhirlpoolServerConfig serverConfig) {
    super(httpClientService, false, serverConfig.getNetworkParameters());
  }
}
