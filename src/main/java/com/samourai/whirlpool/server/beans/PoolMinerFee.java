package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;

public class PoolMinerFee {
  private long minerFeeMin; // in satoshis
  private long minerFeeCap; // in satoshis
  private long minerFeeMax; // in satoshis
  private long minRelayFee; // in satoshis
  private long minerFeeMix; // in satoshis

  public PoolMinerFee(
      WhirlpoolServerConfig.MinerFeeConfig globalMfg,
      WhirlpoolServerConfig.MinerFeeConfig poolMfg,
      int mustMixMin) {
    overrideFrom(globalMfg);
    if (poolMfg != null) {
      overrideFrom(poolMfg);
    }
    this.minerFeeMix = Math.max(minRelayFee, mustMixMin * minerFeeMin);
  }

  private void overrideFrom(WhirlpoolServerConfig.MinerFeeConfig mfg) {
    if (mfg.getMinerFeeMin() > 0) {
      this.minerFeeMin = mfg.getMinerFeeMin();
    }
    if (mfg.getMinerFeeCap() > 0) {
      this.minerFeeCap = mfg.getMinerFeeCap();
    }
    if (mfg.getMinerFeeMax() > 0) {
      this.minerFeeMax = mfg.getMinerFeeMax();
    }
    if (mfg.getMinRelayFee() > 0) {
      this.minRelayFee = mfg.getMinRelayFee();
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

  public long getMinRelayFee() {
    return minRelayFee;
  }

  public long getMinerFeeMix() {
    return minerFeeMix;
  }

  @Override
  public String toString() {
    return "["
        + minerFeeMin
        + "-"
        + minerFeeCap
        + ", max="
        + minerFeeMax
        + "], minRelayFee="
        + minRelayFee
        + ", minerFeeMix="
        + minerFeeMix;
  }
}
