package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;

public class PoolMinerFee {
  private long minerFeeMin; // in satoshis
  private long minerFeeCap; // in satoshis
  private long minerFeeMax; // in satoshis
  private long minRelaySatPerB; // in satoshis
  private long weightTx; // in satoshis
  private long weightPerSurge; // in satoshis

  public PoolMinerFee(
      WhirlpoolServerConfig.PoolMinerFeeConfig globalMfg,
      WhirlpoolServerConfig.PoolMinerFeeConfig poolMfg) {
    overrideFrom(globalMfg);
    if (poolMfg != null) {
      overrideFrom(poolMfg);
    }
  }

  private void overrideFrom(WhirlpoolServerConfig.PoolMinerFeeConfig mfg) {
    if (mfg.getMinerFeeMin() > 0) {
      this.minerFeeMin = mfg.getMinerFeeMin();
    }
    if (mfg.getMinerFeeCap() > 0) {
      this.minerFeeCap = mfg.getMinerFeeCap();
    }
    if (mfg.getMinerFeeMax() > 0) {
      this.minerFeeMax = mfg.getMinerFeeMax();
    }
    if (mfg.getMinRelaySatPerB() > 0) {
      this.minRelaySatPerB = mfg.getMinRelaySatPerB();
    }
    if (mfg.getWeightTx() > 0) {
      this.weightTx = mfg.getWeightTx();
    }
    if (mfg.getWeightPerSurge() > 0) {
      this.weightPerSurge = mfg.getWeightPerSurge();
    }
  }

  public long getMinerFeeMin() {
    return minerFeeMin;
  }

  public long getMinerFeeCap() {
    return minerFeeCap;
  }

  public long getMinerFeeMax() {
    return minerFeeMax;
  }

  public long getMinRelaySatPerB() {
    return minRelaySatPerB;
  }

  public long getWeightTx() {
    return weightTx;
  }

  public long getWeightPerSurge() {
    return weightPerSurge;
  }

  @Override
  public String toString() {
    return "["
        + minerFeeMin
        + "-"
        + minerFeeCap
        + ", max="
        + minerFeeMax
        + "], minRelaySatPerB="
        + minRelaySatPerB
        + ", weightTx"
        + weightTx
        + ", weightPerSurge"
        + weightPerSurge;
  }
}
