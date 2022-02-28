package com.samourai.whirlpool.server.beans.rpc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.server.services.rpc.RpcRawTransactionResponse;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

public class RpcTransaction {
  private int confirmations;
  private long txTime;

  @JsonIgnore private Transaction tx;

  public RpcTransaction(RpcRawTransactionResponse rpcRawTransaction, NetworkParameters params) {
    // parse tx with bitcoinj
    this.tx = TxUtil.getInstance().fromTxHex(params, rpcRawTransaction.getHex());
    this.confirmations = rpcRawTransaction.getConfirmations();
    this.txTime = rpcRawTransaction.getTxTime();
  }

  public int getConfirmations() {
    return confirmations;
  }

  public long getTxTime() {
    return txTime;
  }

  public Transaction getTx() {
    return tx;
  }
}
