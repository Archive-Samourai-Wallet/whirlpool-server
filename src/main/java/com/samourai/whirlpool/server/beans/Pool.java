package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;

public class Pool {
  private String poolId;
  private long denomination; // in satoshis
  private PoolFee poolFee;
  private int minMustMix;
  private int minLiquidity;
  private int surge;
  private int minLiquidityPoolForSurge;
  private int anonymitySet;
  private int tx0MaxOutputs;
  private PoolMinerFee minerFee;
  private long minerFeeMix; // minerFee min required per mix

  private Mix currentMix;
  private InputPool mustMixQueue;
  private InputPool liquidityQueue;

  public Pool(
      String poolId,
      long denomination,
      PoolFee poolFee,
      int minMustMix,
      int minLiquidity,
      int surge,
      int minLiquidityPoolForSurge,
      int anonymitySet,
      int tx0MaxOutputs,
      PoolMinerFee minerFee) {
    this.poolId = poolId;
    this.denomination = denomination;
    this.poolFee = poolFee;
    this.minMustMix = minMustMix;
    this.minLiquidity = minLiquidity;
    this.surge = surge;
    this.minLiquidityPoolForSurge = minLiquidityPoolForSurge;
    this.anonymitySet = anonymitySet;
    this.tx0MaxOutputs = tx0MaxOutputs;
    this.minerFee = minerFee;
    this.minerFeeMix = computeTxSize(0) * minerFee.getMinRelaySatPerB();

    this.mustMixQueue = new InputPool();
    this.liquidityQueue = new InputPool();
  }

  public long computeTxSize(int surges) {
    return minerFee.getWeightTx() + (surges * minerFee.getWeightPerSurge());
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

  public boolean isSurgeDisabledForLowLiquidityPool() {
    return surge > 0 && liquidityQueue.getSize() < minLiquidityPoolForSurge;
  }

  // tests only
  public void _setPoolFee(PoolFee poolFee) {
    this.poolFee = poolFee;
  }

  public long computeMustMixBalanceMin() {
    return denomination + minerFee.getMinerFeeMin();
  }

  public long computeMustMixBalanceCap() {
    return denomination + minerFee.getMinerFeeCap();
  }

  public long computeMustMixBalanceMax() {
    return denomination + minerFee.getMinerFeeMax();
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

  public int getSurge() {
    return surge;
  }

  public int getMinLiquidityPoolForSurge() {
    return minLiquidityPoolForSurge;
  }

  public int getAnonymitySet() {
    return anonymitySet;
  }

  public int getTx0MaxOutputs() {
    return tx0MaxOutputs;
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

  public PoolMinerFee getMinerFee() {
    return minerFee;
  }

  public long getMinerFeeMix() {
    return minerFeeMix;
  }
}
