package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;

public class Pool {
  private String poolId;
  private long denomination; // in satoshis
  private PoolFee poolFee;
  private int minMustMix;
  private int minLiquidity;
  private int anonymitySet;
  private WhirlpoolServerConfig.MinerFeeConfig minerFeeConfig;

  private Mix currentMix;
  private InputPool mustMixQueue;
  private InputPool liquidityQueue;

  public Pool(
      String poolId,
      long denomination,
      PoolFee poolFee,
      int minMustMix,
      int minLiquidity,
      int anonymitySet,
      WhirlpoolServerConfig.MinerFeeConfig minerFeeConfig) {
    this.poolId = poolId;
    this.denomination = denomination;
    this.poolFee = poolFee;
    this.minMustMix = minMustMix;
    this.minLiquidity = minLiquidity;
    this.anonymitySet = anonymitySet;
    this.minerFeeConfig = minerFeeConfig;

    this.mustMixQueue = new InputPool();
    this.liquidityQueue = new InputPool();
  }

  public boolean checkInputBalance(long inputBalance, boolean liquidity) {
    long minBalance = computePremixBalanceMin(liquidity);
    long maxBalance = computePremixBalanceMax(liquidity);
    return inputBalance >= minBalance && inputBalance <= maxBalance;
  }

  public long computePremixBalanceMin(boolean liquidity) {
    return WhirlpoolProtocol.computePremixBalanceMin(
        denomination, computeMustMixBalanceMin(), liquidity);
  }

  public long computePremixBalanceCap(boolean liquidity) {
    return WhirlpoolProtocol.computePremixBalanceMax(
        denomination, computeMustMixBalanceCap(), liquidity);
  }

  public long computePremixBalanceMax(boolean liquidity) {
    return WhirlpoolProtocol.computePremixBalanceMax(
        denomination, computeMustMixBalanceMax(), liquidity);
  }

  public long computeMustMixBalanceMin() {
    return denomination + minerFeeConfig.getMinerFeeMin();
  }

  public long computeMustMixBalanceCap() {
    return denomination + minerFeeConfig.getMinerFeeCap();
  }

  public long computeMustMixBalanceMax() {
    return denomination + minerFeeConfig.getMinerFeeMax();
  }

  public String getPoolId() {
    return poolId;
  }

  public long getDenomination() {
    return denomination;
  }

  public PoolFee getPoolFee() {
    return poolFee;
  }

  public int getMinMustMix() {
    return minMustMix;
  }

  public int getMinLiquidity() {
    return minLiquidity;
  }

  public int getAnonymitySet() {
    return anonymitySet;
  }

  public Mix getCurrentMix() {
    return currentMix;
  }

  public void setCurrentMix(Mix currentMix) {
    this.currentMix = currentMix;
  }

  public InputPool getMustMixQueue() {
    return mustMixQueue;
  }

  public InputPool getLiquidityQueue() {
    return liquidityQueue;
  }

  // for tests
  public WhirlpoolServerConfig.MinerFeeConfig _getMinerFeeConfig() {
    return minerFeeConfig;
  }
}
