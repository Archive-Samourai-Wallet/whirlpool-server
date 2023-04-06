package com.samourai.whirlpool.server.services.rpc;

import com.samourai.soroban.client.rpc.RpcService;
import com.samourai.wallet.crypto.CryptoUtil;
import com.samourai.whirlpool.server.services.JavaHttpClientService;
import org.springframework.stereotype.Service;

@Service
public class RpcServiceServer extends RpcService {
  public RpcServiceServer(JavaHttpClientService httpClientService, CryptoUtil cryptoUtil) {
    super(httpClientService.getHttpClient(), cryptoUtil, false);
  }
}
