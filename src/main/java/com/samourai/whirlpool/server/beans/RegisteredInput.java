package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import org.apache.commons.lang3.BooleanUtils;

public class RegisteredInput {
  private String poolId;
  private String username; // Sender for Soroban clients
  private TxOutPoint outPoint;
  private boolean liquidity;
  private Boolean tor; // null for Soroban clients until confirmed
  private long since;
  private String lastUserHash; // unknown until confirmInput attempt
  private SorobanInput sorobanInput; // null for non-Soroban clients
  private Long confirmingSince; // null until confirming
  private String quarantineReason; // only set when on "quarantine" for current mix
  private byte[] signedBordereau; // after input confirmation

  public RegisteredInput(
      String poolId,
      String username,
      boolean liquidity,
      TxOutPoint outPoint,
      Boolean tor,
      String lastUserHash,
      SorobanInput sorobanInput) {
    this.poolId = poolId;
    this.username = username;
    this.liquidity = liquidity;
    this.outPoint = outPoint;
    this.tor = tor;
    this.since = System.currentTimeMillis();
    this.lastUserHash = lastUserHash;
    this.sorobanInput = sorobanInput;
    this.confirmingSince = null;
    this.quarantineReason = null;
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
    return sorobanInput != null;
  }

  public String getLastUserHash() {
    return lastUserHash;
  }

  public void setLastUserHash(String lastUserHash) {
    this.lastUserHash = lastUserHash;
  }

  public SorobanInput getSorobanInput() {
    return sorobanInput;
  }

  public void setSorobanInput(SorobanInput sorobanInput) {
    this.sorobanInput = sorobanInput;
  }

  public Long getConfirmingSince() {
    return confirmingSince;
  }

  public void setConfirmingSince(Long confirmingSince) {
    this.confirmingSince = confirmingSince;
  }

  public boolean isQuarantine() {
    return quarantineReason != null;
  }

  public String getQuarantineReason() {
    return quarantineReason;
  }

  public void setQuarantineReason(String quarantineReason) {
    this.quarantineReason = quarantineReason;
  }

  public void clearQuarantine() {
    this.quarantineReason = null;
  }

  public byte[] getSignedBordereau() {
    return signedBordereau;
  }

  public void setSignedBordereau(byte[] signedBordereau) {
    this.signedBordereau = signedBordereau;
  }

  @Override
  public String toString() {
    return "poolId="
        + poolId
        + ", outPoint="
        + outPoint
        + ", liquidity="
        + liquidity
        + ", soroban="
        + (sorobanInput != null)
        + ", username="
        + (username != null ? username : "null")
        + ", tor="
        + BooleanUtils.toStringTrueFalse(tor)
        + ", since="
        + since
        + ", lastUserHash="
        + (lastUserHash != null ? lastUserHash : "null")
        + ", confirmingSince="
        + (confirmingSince != null ? confirmingSince : "null");
  }
}
