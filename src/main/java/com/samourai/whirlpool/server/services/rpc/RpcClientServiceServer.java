package com.samourai.whirlpool.server.services.rpc;

import com.samourai.soroban.client.SorobanConfig;
import com.samourai.soroban.client.rpc.RpcClientService;
import org.springframework.stereotype.Service;

@Service
public class RpcClientServiceServer extends RpcClientService {
  public RpcClientServiceServer(SorobanConfig sorobanConfig) {
    super(sorobanConfig);
  }
}
