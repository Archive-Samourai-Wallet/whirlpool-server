package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.fee.WhirlpoolFeeData;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

public class Tx0Validation {
  private Transaction tx;
  private WhirlpoolFeeData feeData;
  private PoolFee poolFee;
  private WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig;
  private int feePercent;
  private TransactionOutput feeOutput;
  private Partner partner;

  public Tx0Validation(
      Transaction tx,
      WhirlpoolFeeData feeData,
      PoolFee poolFee,
      WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig,
      int feePercent,
      TransactionOutput feeOutput,
      Partner partner) {
    this.tx = tx;
    this.feeData = feeData;
    this.poolFee = poolFee;
    this.scodeConfig = scodeConfig;
    this.feePercent = feePercent;
    this.feeOutput = feeOutput;
    this.partner = partner;
  }

  public Collection<Integer> findStrictModeVouts() throws Exception {
    List<Integer> strictModeVouts = new LinkedList<>();
    for (TransactionOutput txOut : tx.getOutputs()) {
      // ignore feeData output (OP_RETURN)
      if (feeData.getTxOutput().getIndex() != txOut.getIndex()) {
        // ignore fee output address
        if (feeOutput == null || feeOutput.getIndex() != txOut.getIndex()) {
          strictModeVouts.add(txOut.getIndex());
        }
      }
    }
    return strictModeVouts;
  }

  public Collection<TransactionOutput> findPremixOutputs(Pool pool) {
    return tx.getOutputs().stream()
        .filter(
            txOut ->
                txOut.getValue().value >= pool.computeMustMixBalanceMin()
                    && txOut.getValue().value <= pool.computeMustMixBalanceCap())
        .collect(Collectors.toList());
  }

  public Transaction getTx() {
    return tx;
  }

  public WhirlpoolFeeData getFeeData() {
    return feeData;
  }

  public PoolFee getPoolFee() {
    return poolFee;
  }

  public WhirlpoolServerConfig.ScodeSamouraiFeeConfig getScodeConfig() {
    return scodeConfig;
  }

  public int getFeePercent() {
    return feePercent;
  }

  public TransactionOutput getFeeOutput() {
    return feeOutput;
  }

  public Partner getPartner() {
    return partner;
  }
}
