package com.samourai.whirlpool.server.beans;

import com.samourai.wallet.util.FeeUtil;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.soroban.WhirlpoolApiCoordinator;

public class Pool {
  private static final FeeUtil feeUtil = FeeUtil.getInstance();

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
  private int txSize;

  private Mix currentMix;
  private InputPoolQueue mustMixQueue;
  private InputPoolQueue liquidityQueue;

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
      PoolMinerFee minerFee,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator) {
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
    this.txSize = feeUtil.estimatedSizeSegwit(0, 0, anonymitySet, anonymitySet, 0);

    this.mustMixQueue = new InputPoolQueue(this, false, whirlpoolApiCoordinator);
    this.liquidityQueue = new InputPoolQueue(this, true, whirlpoolApiCoordinator);
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

  public void clearQuarantine() {
    getLiquidityQueue().clearQuarantine();
    getMustMixQueue().clearQuarantine();
  }

  public boolean isSurgeDisabledForLowLiquidityPool() {
    return surge > 0 && liquidityQueue.getSize() < minLiquidityPoolForSurge;
  }

  public long computePremixValue(long feePerB) {
    long mixFeesEstimate = feeUtil.calculateFee(txSize, feePerB);
    long mixFeePerMustmix = mixFeesEstimate / minMustMix;

    // make sure premixValue is acceptable for pool
    long premixBalanceMin = computePremixBalanceMin(false);
    long premixBalanceCap = computePremixBalanceCap(false);
    long premixBalanceMax = computePremixBalanceMax(false);

    long premixValue = denomination + mixFeePerMustmix;
    premixValue = Math.min(premixValue, premixBalanceMax);
    premixValue = Math.min(premixValue, premixBalanceCap);
    premixValue = Math.max(premixValue, premixBalanceMin);
    return premixValue;
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

  public InputPoolQueue getMustMixQueue() {
    return mustMixQueue;
  }

  public InputPoolQueue getLiquidityQueue() {
    return liquidityQueue;
  }

  public PoolMinerFee getMinerFee() {
    return minerFee;
  }

  public long getMinerFeeMix() {
    return minerFeeMix;
  }
}
