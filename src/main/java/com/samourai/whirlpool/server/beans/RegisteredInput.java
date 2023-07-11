package com.samourai.whirlpool.server.beans;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.utils.Utils;
import org.apache.commons.lang3.BooleanUtils;

public class RegisteredInput {
  private String poolId;
  private String username; // null for Soroban clients until confirmed
  private TxOutPoint outPoint;
  private boolean liquidity;
  private Boolean tor; // null for Soroban clients until confirmed
  private long since;
  private PaymentCode sorobanPaymentCode; // null for non-Soroban clients
  private Long sorobanLastSeen; // last seen time on Soroban
  private String sorobanInitialPayload; // encrypted registerInput payload on Soroban
  private String lastUserHash; // unknown until confirmInput attempt
  private Long confirmingSince; // null until confirming

  public RegisteredInput(
      String poolId,
      String username,
      boolean liquidity,
      TxOutPoint outPoint,
      Boolean tor,
      PaymentCode sorobanPaymentCode,
      String sorobanInitialPayload,
      String lastUserHash) {
    this.poolId = poolId;
    this.username = username;
    this.liquidity = liquidity;
    this.outPoint = outPoint;
    this.tor = tor;
    this.since = System.currentTimeMillis();
    this.sorobanPaymentCode = sorobanPaymentCode;
    this.sorobanLastSeen = null;
    if (sorobanPaymentCode != null) {
      setSorobanLastSeen();
    }
    this.sorobanInitialPayload = sorobanInitialPayload;
    this.lastUserHash = lastUserHash;
    this.confirmingSince = null;
  }

  public long computeMinerFees(Pool pool) {
    return getOutPoint().getValue() - pool.getDenomination();
  }

  public String getPoolId() {
    return poolId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public boolean isLiquidity() {
    return liquidity;
  }

  public TxOutPoint getOutPoint() {
    return outPoint;
  }

  public Boolean getTor() {
    return tor;
  }

  public long getSince() {
    return since;
  }

  public boolean isSoroban() {
    return sorobanPaymentCode != null;
  }

  public PaymentCode getSorobanPaymentCode() {
    return sorobanPaymentCode;
  }

  public Long getSorobanLastSeen() {
    return sorobanLastSeen;
  }

  public void setSorobanLastSeen() {
    this.sorobanLastSeen = System.currentTimeMillis();
  }

  public String getSorobanInitialPayload() {
    return sorobanInitialPayload;
  }

  public String getLastUserHash() {
    return lastUserHash;
  }

  public void setLastUserHash(String lastUserHash) {
    this.lastUserHash = lastUserHash;
  }

  public Long getConfirmingSince() {
    return confirmingSince;
  }

  public void setConfirmingSince(Long confirmingSince) {
    this.confirmingSince = confirmingSince;
  }

  @Override
  public String toString() {
    return "poolId="
        + poolId
        + ", outPoint="
        + outPoint
        + ", liquidity="
        + liquidity
        + ", username="
        + (username != null ? username : "null")
        + ", tor="
        + BooleanUtils.toStringTrueFalse(tor)
        + ", since="
        + since
        + ", sorobanPaymentCode="
        + (sorobanPaymentCode != null
            ? Utils.obfuscateString(sorobanPaymentCode.toString(), 3)
            : "null")
        + (sorobanLastSeen != null ? sorobanLastSeen : "null")
        + ", sorobanInitialPayload="
        + (sorobanInitialPayload != null ? Utils.obfuscateString(sorobanInitialPayload, 3) : "null")
        + ", lastUserHash="
        + (lastUserHash != null ? lastUserHash : "null")
        + ", confirmingSince="
        + (confirmingSince != null ? confirmingSince : "null");
  }
}
