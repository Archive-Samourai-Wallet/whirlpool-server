package com.samourai.whirlpool.server.controllers.rest.beans;

public class SamouraiConfig {

  // extlibj: BackendServer
  private String backendServerMainnetClear = "https://api.samouraiwallet.com/v2";
  private String backendServerMainnetOnion =
      "http://d2oagweysnavqgcfsfawqwql2rwxend7xxpriq676lzsmtfwbt75qbqd.onion/v2";
  private String backendServerTestnetClear = "https://api.samouraiwallet.com/test/v2";
  private String backendServerTestnetOnion =
      "http://d2oagweysnavqgcfsfawqwql2rwxend7xxpriq676lzsmtfwbt75qbqd.onion/test/v2";
  // TODO extlibj: SorobanServer

  public SamouraiConfig() {}

  public String getBackendServerMainnetClear() {
    return backendServerMainnetClear;
  }

  public void setBackendServerMainnetClear(String backendServerMainnetClear) {
    this.backendServerMainnetClear = backendServerMainnetClear;
  }

  public String getBackendServerMainnetOnion() {
    return backendServerMainnetOnion;
  }

  public void setBackendServerMainnetOnion(String backendServerMainnetOnion) {
    this.backendServerMainnetOnion = backendServerMainnetOnion;
  }

  public String getBackendServerTestnetClear() {
    return backendServerTestnetClear;
  }

  public void setBackendServerTestnetClear(String backendServerTestnetClear) {
    this.backendServerTestnetClear = backendServerTestnetClear;
  }

  public String getBackendServerTestnetOnion() {
    return backendServerTestnetOnion;
  }

  public void setBackendServerTestnetOnion(String backendServerTestnetOnion) {
    this.backendServerTestnetOnion = backendServerTestnetOnion;
  }
}
