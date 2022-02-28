package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.lang.invoke.MethodHandles;
import java.util.Map.Entry;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScodeService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WhirlpoolServerConfig serverConfig;

  public ScodeService(WhirlpoolServerConfig serverConfig) throws Exception {
    this.serverConfig = serverConfig;
  }

  protected WhirlpoolServerConfig.ScodeSamouraiFeeConfig getByPayload(
      short scodePayload, long tx0Time) {
    // find
    Optional<Entry<String, WhirlpoolServerConfig.ScodeSamouraiFeeConfig>> feePayloadEntry =
        serverConfig.getSamouraiFees().getScodes().entrySet().stream()
            .filter(e -> e.getValue().getPayload() == scodePayload)
            .findFirst();
    if (!feePayloadEntry.isPresent()) {
      log.warn("No SCode found for payload=" + scodePayload);
      return null;
    }
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig = feePayloadEntry.get().getValue();

    // check expiry
    if (!scodeConfig.isValidAt(tx0Time)) {
      return null;
    }
    return scodeConfig;
  }

  public WhirlpoolServerConfig.ScodeSamouraiFeeConfig getByScode(String scode, long tx0Time) {
    // find
    String scodeUpperCase = (!StringUtils.isEmpty(scode) ? scode.toUpperCase() : null);
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig =
        serverConfig.getSamouraiFees().getScodes().get(scodeUpperCase);
    if (scodeConfig == null) {
      return null;
    }

    // check expiry
    if (!scodeConfig.isValidAt(tx0Time)) {
      return null;
    }
    return scodeConfig;
  }
}
