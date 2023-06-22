package com.samourai.whirlpool.server.beans;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.utils.Utils;
import org.apache.commons.lang3.BooleanUtils;

public class RegisteredInput {
  private String poolId;
  private String username;
  private TxOutPoint outPoint;
  private boolean liquidity;
  private Boolean tor;
  private PaymentCode sorobanPaymentCode; // null for non-Soroban clients
  private Long sorobanLastSeen; // last seen time on Soroban
  private String lastUserHash; // unknown until confirmInput attempt

  public RegisteredInput(
      String poolId,
      String username,
      boolean liquidity,
      TxOutPoint outPoint,
      Boolean tor,
      PaymentCode sorobanPaymentCode,
      String lastUserHash) {
    this.poolId = poolId;
    this.username = username;
    this.liquidity = liquidity;
    this.outPoint = outPoint;
    this.tor = tor;
    this.sorobanPaymentCode = sorobanPaymentCode;
    this.sorobanLastSeen = null;
    if (sorobanPaymentCode != null) {
      setSorobanLastSeen();
    }
    this.lastUserHash = lastUserHash;
  }

  public Long getSorobanLastSeen() {
    return sorobanLastSeen;
  }

  public void setSorobanLastSeen() {
    this.sorobanLastSeen = System.currentTimeMillis();
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

  public boolean isLiquidity() {
    return liquidity;
  }

  public TxOutPoint getOutPoint() {
    return outPoint;
  }

  public Boolean getTor() {
    return tor;
  }

  public boolean isSoroban() {
    return sorobanPaymentCode != null;
  }

  public PaymentCode getSorobanPaymentCode() {
    return sorobanPaymentCode;
  }

  public String getLastUserHash() {
    return lastUserHash;
  }

  public void setLastUserHash(String lastUserHash) {
    this.lastUserHash = lastUserHash;
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
                + username
                + ", tor="
                + BooleanUtils.toStringTrueFalse(tor)
                + ", sorobanPaymentCode="
                + (sorobanPaymentCode!= null? Utils.obfuscateString(sorobanPaymentCode.toString(), 3): "null")
            + ",lastUserHash=" + (lastUserHash != null ? lastUserHash : "null");
  }
}
