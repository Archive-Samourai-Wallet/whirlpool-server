package com.samourai.whirlpool.server.beans;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.utils.Utils;

public class RegisteredInput {
  private static final String IP_TOR = "127.0.0.1";

  private String poolId;
  private String username;
  private TxOutPoint outPoint;
  private boolean liquidity;
  private String ip;
  private PaymentCode sorobanPaymentCode; // null for non-Soroban clients
  private Long sorobanHeartbeat; // last seen time on Soroban
  private String lastUserHash; // unknown until confirmInput attempt

  public RegisteredInput(
      String poolId,
      String username,
      boolean liquidity,
      TxOutPoint outPoint,
      String ip,
      PaymentCode sorobanPaymentCode,
      String lastUserHash) {
    this.poolId = poolId;
    this.username = username;
    this.liquidity = liquidity;
    this.outPoint = outPoint;
    this.ip = ip;
    this.sorobanPaymentCode = sorobanPaymentCode;
    this.lastUserHash = lastUserHash;
  }

  public void setSorobanHeartBeat() {
    this.sorobanHeartbeat = System.currentTimeMillis();
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

  public String getIp() {
    return ip;
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

  public boolean isTor() {
    return IP_TOR.equals(ip);
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
                + ", ip="
                + ip
                + ", sorobanPaymentCode="
                + Utils.obfuscateString(sorobanPaymentCode.toString(), 3)
            != null
        ? sorobanPaymentCode.toString()
        : "null" + ",lastUserHash=" + (lastUserHash != null ? lastUserHash : "null");
  }
}
