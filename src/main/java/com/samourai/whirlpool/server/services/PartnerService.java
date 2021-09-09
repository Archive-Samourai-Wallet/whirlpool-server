package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.Partner;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PartnerService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WhirlpoolServerConfig whirlpoolServerConfig;
  private Map<String, Partner> partnersById;
  private Map<Short, Partner> partnersByPayload;

  @Autowired
  public PartnerService(WhirlpoolServerConfig whirlpoolServerConfig) throws Exception {
    this.whirlpoolServerConfig = whirlpoolServerConfig;

    this.partnersById = new LinkedHashMap<>();
    for (WhirlpoolServerConfig.PartnerConfig partnerConfig : whirlpoolServerConfig.getPartners()) {
      Partner partner = new Partner(partnerConfig);
      this.partnersById.put(partner.getId(), partner);
      if (log.isDebugEnabled()) {
        log.debug("+partner: " + partner.toString());
      }
    }

    this.partnersByPayload = new LinkedHashMap<>();
    for (Partner partner : partnersById.values()) {
      this.partnersByPayload.put(partner.getPayload(), partner);
    }
  }

  public Collection<Partner> getPartners() {
    return partnersById.values();
  }

  public Partner getById(String partnerId) throws IllegalInputException {
    Partner partner = partnersById.get(partnerId);
    if (partner == null) {
      throw new IllegalInputException("Partner not found: partnerId=" + partnerId);
    }
    return partner;
  }

  public Partner getByPayload(short partnerPayload) throws IllegalInputException {
    Partner partner = partnersByPayload.get(partnerPayload);
    if (partner == null) {
      throw new IllegalInputException("Partner not found: partnerPayload" + partnerPayload);
    }
    return partner;
  }
}
