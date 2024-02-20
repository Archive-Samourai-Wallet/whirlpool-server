package com.samourai.whirlpool.server.utils;

import com.samourai.whirlpool.client.mix.handler.MixDestination;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.server.beans.MixStatus;

public class MultiClientListener implements WhirlpoolClientListener {
  // indice 0 is always null as currentMix starts from 1
  private MixStatus mixStatus;
  private MixStep mixStep;
  private MultiClientManager multiClientManager;

  public MultiClientListener(MultiClientManager multiClientManager) {
    this.multiClientManager = multiClientManager;
  }

  @Override
  public void success(String mixId, Utxo receiveUtxo, MixDestination receiveDestination) {
    mixStatus = MixStatus.SUCCESS;
    notifyMultiClientManager();
  }

  @Override
  public void progress(String mixId, MixStep step) {
    mixStep = step;
  }

  @Override
  public void fail(String mixId, MixFailReason reason, String notifiableError) {
    mixStatus = MixStatus.FAIL;
    notifyMultiClientManager();
  }

  private void notifyMultiClientManager() {
    synchronized (multiClientManager) {
      multiClientManager.notify();
    }
  }

  public MixStatus getMixStatus() {
    return mixStatus;
  }

  public MixStep getMixStep() {
    return mixStep;
  }
}
