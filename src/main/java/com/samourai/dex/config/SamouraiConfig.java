package com.samourai.dex.config;
// TODO temporary waiting dexconfig merge
public class SamouraiConfig {
  // extlibj: BackendServer
  private String backendServerMainnetClear = "https://api.samouraiwallet.com/v2";
  private String backendServerMainnetOnion =
      "http://d2oagweysnavqgcfsfawqwql2rwxend7xxpriq676lzsmtfwbt75qbqd.onion/v2";
  private String backendServerTestnetClear = "https://api.samouraiwallet.com/test/v2";
  private String backendServerTestnetOnion =
      "http://d2oagweysnavqgcfsfawqwql2rwxend7xxpriq676lzsmtfwbt75qbqd.onion/test/v2";

  // extlibj: SorobanServer
  private String sorobanServerTestnetClear = "https://82.221.128.176/test";
  private String sorobanServerTestnetOnion =
      "http://sorob4sg7yiopktgz4eom7hl5mcodr6quvhmdpljl5qqhmt6po7oebid.onion/test";
  private String sorobanServerMainnetClear = "https://82.221.128.176";
  private String sorobanServerMainnetOnion =
      "http://sorob4sg7yiopktgz4eom7hl5mcodr6quvhmdpljl5qqhmt6po7oebid.onion";

  // whrilpool-client: WhrilpoolServer
  private String whirlpoolServerTestnetClear = "https://82.221.131.94:8081";
  private String whirlpoolServerTestnetOnion =
      "http://y5qvjlxvbohc73slq4j4qldoegyukvpp74mbsrjosnrsgg7w5fon6nyd.onion";
  private String whirlpoolServerMainnetClear = "https://82.221.131.94:8080";
  private String whirlpoolServerMainnetOnion =
      "http://udkmfc5j6zvv3ysavbrwzhwji4hpyfe3apqa6yst7c7l32mygf65g4ad.onion";
  private String whirlpoolServerIntegrationClear = "https://82.221.131.94:8082";
  private String whirlpoolServerIntegrationOnion =
      "http://yuvewbfkftftcbzn54lfx3i5s4jxr4sfgpsbkvcflgzcvumyxrkopmyd.onion";

  public String getWhirlpoolServerIntegrationClear() {
    return whirlpoolServerIntegrationClear;
  }

  public void setWhirlpoolServerIntegrationClear(String whirlpoolServerIntegrationClear) {
    this.whirlpoolServerIntegrationClear = whirlpoolServerIntegrationClear;
  }

  public String getWhirlpoolServerIntegrationOnion() {
    return whirlpoolServerIntegrationOnion;
  }

  public void setWhirlpoolServerIntegrationOnion(String whirlpoolServerIntegrationOnion) {
    this.whirlpoolServerIntegrationOnion = whirlpoolServerIntegrationOnion;
  }

  public String getWhirlpoolServerTestnetClear() {
    return whirlpoolServerTestnetClear;
  }

  public void setWhirlpoolServerTestnetClear(String whirlpoolServerTestnetClear) {
    this.whirlpoolServerTestnetClear = whirlpoolServerTestnetClear;
  }

  public String getWhirlpoolServerTestnetOnion() {
    return whirlpoolServerTestnetOnion;
  }

  public void setWhirlpoolServerTestnetOnion(String whirlpoolServerTestnetOnion) {
    this.whirlpoolServerTestnetOnion = whirlpoolServerTestnetOnion;
  }

  public String getWhirlpoolServerMainnetClear() {
    return whirlpoolServerMainnetClear;
  }

  public void setWhirlpoolServerMainnetClear(String whirlpoolServerMainnetClear) {
    this.whirlpoolServerMainnetClear = whirlpoolServerMainnetClear;
  }

  public String getWhirlpoolServerMainnetOnion() {
    return whirlpoolServerMainnetOnion;
  }

  public void setWhirlpoolServerMainnetOnion(String whirlpoolServerMainnetOnion) {
    this.whirlpoolServerMainnetOnion = whirlpoolServerMainnetOnion;
  }

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

  public String getSorobanServerTestnetClear() {
    return sorobanServerTestnetClear;
  }

  public void setSorobanServerTestnetClear(String sorobanServerTestnetClear) {
    this.sorobanServerTestnetClear = sorobanServerTestnetClear;
  }

  public String getSorobanServerTestnetOnion() {
    return sorobanServerTestnetOnion;
  }

  public void setSorobanServerTestnetOnion(String sorobanServerTestnetOnion) {
    this.sorobanServerTestnetOnion = sorobanServerTestnetOnion;
  }

  public String getSorobanServerMainnetClear() {
    return sorobanServerMainnetClear;
  }

  public void setSorobanServerMainnetClear(String sorobanServerMainnetClear) {
    this.sorobanServerMainnetClear = sorobanServerMainnetClear;
  }

  public String getSorobanServerMainnetOnion() {
    return sorobanServerMainnetOnion;
  }

  public void setSorobanServerMainnetOnion(String sorobanServerMainnetOnion) {
    this.sorobanServerMainnetOnion = sorobanServerMainnetOnion;
  }
}
