package com.samourai.whirlpool.server.services.fee;

import org.bitcoinj.core.TransactionOutput;

public class WhirlpoolFeeOutput {
  private TransactionOutput txOutput;
  private byte[] opReturnValue;

  public WhirlpoolFeeOutput(TransactionOutput txOutput, byte[] opReturnValue) {
    this.txOutput = txOutput;
    this.opReturnValue = opReturnValue;
  }

  public TransactionOutput getTxOutput() {
    return txOutput;
  }

  public byte[] getOpReturnValue() {
    return opReturnValue;
  }
}
