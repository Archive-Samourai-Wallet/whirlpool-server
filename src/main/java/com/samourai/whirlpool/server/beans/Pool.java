package com.samourai.whirlpool.server.beans;

import com.samourai.wallet.util.FeeUtil;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;

public class Pool {
  private static final FeeUtil feeUtil = FeeUtil.getInstance();

  private String poolId;
  private long denomination; // in satoshis
  private PoolFee poolFee;
  private int minMustMix;
  private int minLiquidity;
  private int anonymitySet;
  private int tx0MaxOutputs;
  private PoolMinerFee minerFee;
  private int txSize;

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
      int tx0MaxOutputs,
      PoolMinerFee minerFee) {
    this.poolId = poolId;
    this.denomination = denomination;
    this.poolFee = poolFee;
    this.minMustMix = minMustMix;
    this.minLiquidity = minLiquidity;
    this.anonymitySet = anonymitySet;
    this.tx0MaxOutputs = tx0MaxOutputs;
    this.minerFee = minerFee;
    this.txSize = feeUtil.estimatedSizeSegwit(0, 0, anonymitySet, anonymitySet, 0);

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

  public int getAnonymitySet() {
    return anonymitySet;
  }

  public int getTx0MaxOutputs() {
    return tx0MaxOutputs;
  }

  public long getMinerFeeMix() {
    return minerFee.getMinerFeeMix();
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
}
