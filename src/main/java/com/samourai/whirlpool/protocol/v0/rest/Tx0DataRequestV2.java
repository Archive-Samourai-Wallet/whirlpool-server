package com.samourai.whirlpool.protocol.v0.rest;

public class Tx0DataRequestV2 {
  public String scode;
  public String partnerId;
  @Deprecated // not used anymore
  public boolean cascading;

  public Tx0DataRequestV2() {}

  public Tx0DataRequestV2(String scode, String partnerId) {
    this.scode = scode;
    this.partnerId = partnerId;
  }
}
