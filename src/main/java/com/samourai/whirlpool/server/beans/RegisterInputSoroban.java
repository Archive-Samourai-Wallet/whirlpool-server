package com.samourai.whirlpool.server.beans;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.protocol.soroban.RegisterInputSorobanMessage;

public class RegisterInputSoroban {
  private RegisterInputSorobanMessage sorobanMessage;
  private PaymentCode sorobanPaymentCode;

  public RegisterInputSoroban(
      RegisterInputSorobanMessage sorobanMessage, PaymentCode sorobanPaymentCode) {
    this.sorobanMessage = sorobanMessage;
    this.sorobanPaymentCode = sorobanPaymentCode;
  }

  public RegisterInputSorobanMessage getSorobanMessage() {
    return sorobanMessage;
  }

  public PaymentCode getSorobanPaymentCode() {
    return sorobanPaymentCode;
  }
}
