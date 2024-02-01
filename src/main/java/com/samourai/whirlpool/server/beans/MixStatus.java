package com.samourai.whirlpool.server.beans;

public enum MixStatus {
  CONFIRM_INPUT(
      com.samourai.whirlpool.protocol.websocket.notifications.MixStatus.CONFIRM_INPUT,
      com.samourai.whirlpool.protocol.soroban.payload.beans.MixStatus.CONFIRM_INPUT),
  REGISTER_OUTPUT(
      com.samourai.whirlpool.protocol.websocket.notifications.MixStatus.REGISTER_OUTPUT,
      com.samourai.whirlpool.protocol.soroban.payload.beans.MixStatus.REGISTER_OUTPUT),
  REVEAL_OUTPUT(
      com.samourai.whirlpool.protocol.websocket.notifications.MixStatus.REVEAL_OUTPUT,
      com.samourai.whirlpool.protocol.soroban.payload.beans.MixStatus.REVEAL_OUTPUT),
  SIGNING(
      com.samourai.whirlpool.protocol.websocket.notifications.MixStatus.SIGNING,
      com.samourai.whirlpool.protocol.soroban.payload.beans.MixStatus.SIGNING),
  SUCCESS(
      com.samourai.whirlpool.protocol.websocket.notifications.MixStatus.SUCCESS,
      com.samourai.whirlpool.protocol.soroban.payload.beans.MixStatus.SUCCESS),
  FAIL(
      com.samourai.whirlpool.protocol.websocket.notifications.MixStatus.FAIL,
      com.samourai.whirlpool.protocol.soroban.payload.beans.MixStatus.FAIL),
  ;

  private com.samourai.whirlpool.protocol.websocket.notifications.MixStatus mixStatusV0;
  private com.samourai.whirlpool.protocol.soroban.payload.beans.MixStatus mixStatus;

  MixStatus(
      com.samourai.whirlpool.protocol.websocket.notifications.MixStatus mixStatusV0,
      com.samourai.whirlpool.protocol.soroban.payload.beans.MixStatus mixStatus) {
    this.mixStatusV0 = mixStatusV0;
    this.mixStatus = mixStatus;
  }

  public com.samourai.whirlpool.protocol.websocket.notifications.MixStatus getMixStatusV0() {
    return mixStatusV0;
  }

  public com.samourai.whirlpool.protocol.soroban.payload.beans.MixStatus getMixStatus() {
    return mixStatus;
  }
}
