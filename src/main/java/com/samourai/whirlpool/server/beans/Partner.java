package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.xmanager.protocol.XManagerService;

public class Partner {
  private String id;
  private short payload;
  private XManagerService xmService;

  public Partner(WhirlpoolServerConfig.PartnerConfig partnerConfig) throws Exception {
    this.id = partnerConfig.getId();
    this.payload = partnerConfig.getPayload();

    String xms = partnerConfig.getXmService();
    this.xmService = XManagerService.valueOf(xms);
    if (this.xmService == null) {
      throw new Exception("XManagerService not found: " + xms + " for partnerId=" + this.id);
    }
  }

  public String getId() {
    return id;
  }

  public short getPayload() {
    return payload;
  }

  public XManagerService getXmService() {
    return xmService;
  }

  @Override
  public String toString() {
    return "id=" + id + ", payload=" + payload + ", xmService=" + xmService.name();
  }
}
