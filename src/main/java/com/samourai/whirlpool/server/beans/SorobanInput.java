package com.samourai.whirlpool.server.beans;

import com.samourai.soroban.client.endpoint.meta.typed.SorobanEndpointTyped;
import com.samourai.wallet.bip47.rpc.PaymentCode;

public class SorobanInput {
  private PaymentCode sender;
  private Long sorobanLastSeen; // last seen time on Soroban
  private SorobanEndpointTyped endpointRegisterInputReply; // for RegisterInputResponse

  public SorobanInput(PaymentCode sender, SorobanEndpointTyped endpointRegisterInputReply) {
    this.sender = sender;
    setSorobanLastSeen();
    this.endpointRegisterInputReply = endpointRegisterInputReply;
  }

  public PaymentCode getSender() {
    return sender;
  }

  public Long getSorobanLastSeen() {
    return sorobanLastSeen;
  }

  public void setSorobanLastSeen() {
    this.sorobanLastSeen = System.currentTimeMillis();
  }

  public SorobanEndpointTyped getEndpointRegisterInputReply() {
    return endpointRegisterInputReply;
  }

  @Override
  public String toString() {
    return "sender=" + sender.toString() + ", sorobanLastSeen=" + sorobanLastSeen;
  }
}
