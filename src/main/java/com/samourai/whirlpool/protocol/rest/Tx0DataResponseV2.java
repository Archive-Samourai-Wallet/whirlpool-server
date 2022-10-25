//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.samourai.whirlpool.protocol.rest;

import com.samourai.whirlpool.server.beans.TxOutSignature;

public class Tx0DataResponseV2 {
  public Tx0DataResponseV2.Tx0Data[] tx0Datas;

  public Tx0DataResponseV2() {}

  public Tx0DataResponseV2(Tx0DataResponseV2.Tx0Data[] tx0Datas) {
    this.tx0Datas = tx0Datas;
  }

  public static class Tx0Data {
    public String poolId;
    public String feePaymentCode;
    public long feeValue;
    public long feeChange;
    public int feeDiscountPercent;
    public String message;
    public String feePayload64;
    public String feeAddress;
    public String feeOutputSigningAddress;
    public String feeOutputPreHash;
    public String feeOutputSignature;

    public Tx0Data() {}

    public Tx0Data(
        String poolId,
        String feePaymentCode,
        long feeValue,
        long feeChange,
        int feeDiscountPercent,
        String message,
        String feePayload64,
        String feeAddress,
        TxOutSignature txOutSignature) {
      this.poolId = poolId;
      this.feePaymentCode = feePaymentCode;
      this.feeValue = feeValue;
      this.feeChange = feeChange;
      this.feeDiscountPercent = feeDiscountPercent;
      this.message = message;
      this.feePayload64 = feePayload64;
      this.feeAddress = feeAddress;
      this.feeOutputSigningAddress = txOutSignature != null ? txOutSignature.signingAddress : null;
      this.feeOutputPreHash = txOutSignature != null ? txOutSignature.preHash : null;
      this.feeOutputSignature = txOutSignature != null ? txOutSignature.signature : null;
    }
  }
}
