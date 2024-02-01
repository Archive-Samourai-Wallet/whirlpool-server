package com.samourai.whirlpool.server.beans.rpc;

import com.samourai.whirlpool.client.mix.handler.UtxoWithBalance;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.server.utils.Utils;

public class TxOutPoint {
  private String hash;
  private long index;
  private long value;
  private int confirmations;
  private byte[] scriptBytes;
  private String toAddress;

  public TxOutPoint(
      String hash,
      long index,
      long value,
      int confirmations,
      byte[] scriptBytes,
      String toAddress) {
    this.hash = hash;
    this.index = index;
    this.value = value;
    this.confirmations = confirmations;
    this.scriptBytes = scriptBytes;
    this.toAddress = toAddress;
  }

  public String getHash() {
    return hash;
  }

  public long getIndex() {
    return index;
  }

  public long getValue() {
    return value;
  }

  public int getConfirmations() {
    return confirmations;
  }

  public byte[] getScriptBytes() {
    return scriptBytes;
  }

  public String getToAddress() {
    return toAddress;
  }

  public String toKey() {
    return Utils.computeOutpointId(this);
  }

  public Utxo toUtxo() {
    return new Utxo(hash, index);
  }

  public UtxoWithBalance toUtxoWithBalance() {
    return new UtxoWithBalance(toUtxo(), value);
  }

  @Override
  public String toString() {
    return toKey() + " (" + value + "sats, " + confirmations + " confirmations)";
  }
}
