package com.samourai.whirlpool.server.beans;

import com.samourai.soroban.client.endpoint.meta.typed.SorobanEndpointTyped;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SorobanInput {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
    if (log.isDebugEnabled()) {
      log.debug("INPUT_SOROBAN_LASTSEEN " + sender);
    }
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
