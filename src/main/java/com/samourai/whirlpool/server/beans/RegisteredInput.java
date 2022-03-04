package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;

public class RegisteredInput {
  private static final String IP_TOR = "127.0.0.1";

  private String poolId;
  private String username;
  private TxOutPoint outPoint;
  private boolean liquidity;
  private String ip;
  private String lastUserHash; // unknown until confirmInput attempt

  public RegisteredInput(
      String poolId,
      String username,
      boolean liquidity,
      TxOutPoint outPoint,
      String ip,
      String lastUserHash) {
    this.poolId = poolId;
    this.username = username;
    this.liquidity = liquidity;
    this.outPoint = outPoint;
    this.ip = ip;
    this.lastUserHash = lastUserHash;
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
        + ",lastUserHash="
        + (lastUserHash != null ? lastUserHash : "null");
  }
}
