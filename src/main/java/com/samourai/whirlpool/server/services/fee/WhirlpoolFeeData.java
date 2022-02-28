package com.samourai.whirlpool.server.services.fee;

import org.bitcoinj.core.TransactionOutput;

public class WhirlpoolFeeData {

  private int feeIndice;
  private short scodePayload;
  private short partnerPayload;
  private TransactionOutput txOutput;

  public WhirlpoolFeeData(
      int feeIndice, short scodePayload, short partnerPayload, TransactionOutput txOutput) {
    this.feeIndice = feeIndice;
    this.scodePayload = scodePayload;
    this.partnerPayload = partnerPayload;
    this.txOutput = txOutput;
  }

  public int getFeeIndice() {
    return feeIndice;
  }

  public short getScodePayload() {
    return scodePayload;
  }

  public short getPartnerPayload() {
    return partnerPayload;
  }

  public TransactionOutput getTxOutput() {
    return txOutput;
  }

  @Override
  public String toString() {
    return "feeIndice="
        + feeIndice
        + ", scodePayload="
        + scodePayload
        + ", partnerPayload="
        + partnerPayload
        + ", txOutputIndex="
        + txOutput.getIndex();
  }
}
