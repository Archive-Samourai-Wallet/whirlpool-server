package com.samourai.whirlpool.server.beans.event;

import com.samourai.whirlpool.client.wallet.beans.WhirlpoolEvent;
import com.samourai.whirlpool.server.beans.Mix;

public class MixStopEvent extends WhirlpoolEvent {
  private Mix mix;

  public MixStopEvent(Mix mix) {
    this.mix = mix;
  }

  public Mix getMix() {
    return mix;
  }
}
