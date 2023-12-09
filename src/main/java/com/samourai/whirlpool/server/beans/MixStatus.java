package com.samourai.whirlpool.server.beans;

public enum MixStatus {
  CONFIRM_INPUT(com.samourai.whirlpool.protocol.websocket.notifications.MixStatus.CONFIRM_INPUT),
  REGISTER_OUTPUT(
      com.samourai.whirlpool.protocol.websocket.notifications.MixStatus.REGISTER_OUTPUT),
  REVEAL_OUTPUT(com.samourai.whirlpool.protocol.websocket.notifications.MixStatus.REVEAL_OUTPUT),
  SIGNING(com.samourai.whirlpool.protocol.websocket.notifications.MixStatus.SIGNING),
  SUCCESS(com.samourai.whirlpool.protocol.websocket.notifications.MixStatus.SUCCESS),
  FAIL(com.samourai.whirlpool.protocol.websocket.notifications.MixStatus.FAIL),
  ;

  private com.samourai.whirlpool.protocol.websocket.notifications.MixStatus mixStatusV0;

  MixStatus(com.samourai.whirlpool.protocol.websocket.notifications.MixStatus mixStatusV0) {
    this.mixStatusV0 = mixStatusV0;
  }

  public com.samourai.whirlpool.protocol.websocket.notifications.MixStatus getMixStatusV0() {
    return mixStatusV0;
  }
}
