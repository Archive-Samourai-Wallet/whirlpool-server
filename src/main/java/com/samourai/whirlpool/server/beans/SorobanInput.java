package com.samourai.whirlpool.server.beans;

import com.samourai.wallet.bip47.rpc.Bip47Partner;
import com.samourai.whirlpool.server.utils.Utils;

public class SorobanInput {
  private Bip47Partner bip47Partner;
  private Long sorobanLastSeen; // last seen time on Soroban
  private String requestId; // set from registerInputRequest
  private int requestNonce;

  public SorobanInput(Bip47Partner bip47Partner, String requestId) {
    this.bip47Partner = bip47Partner;
    this.requestId = requestId;
    this.requestNonce = 0;
    setSorobanLastSeen();
  }

  public Bip47Partner getBip47Partner() {
    return bip47Partner;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
    this.requestNonce = 0;
  }

  public int getRequestNonceAndIncrement() {
    return requestNonce++;
  }

  public Long getSorobanLastSeen() {
    return sorobanLastSeen;
  }

  public void setSorobanLastSeen() {
    this.sorobanLastSeen = System.currentTimeMillis();
  }

  @Override
  public String toString() {
    return "bip47Partner="
        + Utils.obfuscateString(bip47Partner.getPaymentCodePartner().toString(), 3)
        + ", sorobanLastSeen="
        + sorobanLastSeen;
  }
}
