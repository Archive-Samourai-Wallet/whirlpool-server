package com.samourai.whirlpool.server.beans;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.protocol.soroban.RegisterInputSorobanMessage;

public class RegisterInputSoroban {
  private RegisterInputSorobanMessage sorobanMessage;
  private PaymentCode sorobanPaymentCode;
  private String initialPayload;

  public RegisterInputSoroban(
      RegisterInputSorobanMessage sorobanMessage,
      PaymentCode sorobanPaymentCode,
      String initialPayload) {
    this.sorobanMessage = sorobanMessage;
    this.sorobanPaymentCode = sorobanPaymentCode;
    this.initialPayload = initialPayload;
  }

  public RegisterInputSorobanMessage getSorobanMessage() {
    return sorobanMessage;
  }

  public PaymentCode getSorobanPaymentCode() {
    return sorobanPaymentCode;
  }

  public String getInitialPayload() {
    return initialPayload;
  }
}
