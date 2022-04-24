package com.samourai.whirlpool.server.services.fee;

import org.bitcoinj.core.TransactionOutput;

public class WhirlpoolFeeData {

  private int feeIndice;
  private short scodePayload;
  private short partnerPayload;
  private TransactionOutput txOutput;
  private short opReturnVersion;
  private short feePayloadVersion;

  public WhirlpoolFeeData(
      int feeIndice,
      short scodePayload,
      short partnerPayload,
      TransactionOutput txOutput,
      short opReturnVersion,
      short feePayloadVersion) {
    this.feeIndice = feeIndice;
    this.scodePayload = scodePayload;
    this.partnerPayload = partnerPayload;
    this.txOutput = txOutput;
    this.opReturnVersion = opReturnVersion;
    this.feePayloadVersion = feePayloadVersion;
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

  public short getOpReturnVersion() {
    return opReturnVersion;
  }

  public short getFeePayloadVersion() {
    return feePayloadVersion;
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
        + txOutput.getIndex()
        + ", feeOpReturnVersion="
        + opReturnVersion
        + ", feePayloadVersion="
        + feePayloadVersion;
  }
}
