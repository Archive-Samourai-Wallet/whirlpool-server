package com.samourai.whirlpool.server.controllers.web;

import com.samourai.wallet.util.Util;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.utils.Utils;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// used in thymeleaf templates
@Component
public class TemplateUtil {
  public BigDecimal satoshisToBtc(long sats) {
    return Utils.satoshisToBtc(sats);
  }

  public String registeredInputsToString(Collection<RegisteredInput> registeredInputs) {
    String result = registeredInputs.size() + " inputs";
    if (registeredInputs.size() > 0) {
      result +=
          ":\n"
              + registeredInputs.stream()
                  .map(
                      input ->
                          input.getOutPoint().toKey()
                              + " "
                              + (input.isSoroban() ? "soroban" : "classic")
                              + (input.isQuarantine() ? ": " + input.getQuarantineReason() : ""))
                  .collect(Collectors.joining("\n"));
    }
    return result;
  }

  public String durationFromNow(long ms) {
    return Util.formatDurationFromNow(ms);
  }

  public String duration(int ms) {
    return Util.formatDuration(ms);
  }
}
