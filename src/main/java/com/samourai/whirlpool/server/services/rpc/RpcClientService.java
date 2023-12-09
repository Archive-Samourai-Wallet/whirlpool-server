package com.samourai.whirlpool.server.services.rpc;

import java.util.Optional;

public interface RpcClientService {
  boolean testConnectivity();

  Optional<RpcRawTransactionResponse> getRawTransaction(String txid);

  boolean isTxOutUnspent(String txid, long index);

  int getBlockHeight() throws Exception;
}
