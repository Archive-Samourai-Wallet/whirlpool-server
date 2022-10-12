package com.samourai.whirlpool.server.beans;

public class TxOutSignature {
  public String signingAddress;
  public String preHash;
  public String signature;

  public TxOutSignature(String signingAddress, String preHash, String signature) {
    this.signingAddress = signingAddress;
    this.preHash = preHash;
    this.signature = signature;
  }

  @Override
  public String toString() {
    return "TxOutSignature{"
        + "signingAddress='"
        + signingAddress
        + '\''
        + ", preHash='"
        + preHash
        + '\''
        + ", signature='"
        + signature
        + '\''
        + '}';
  }
}
