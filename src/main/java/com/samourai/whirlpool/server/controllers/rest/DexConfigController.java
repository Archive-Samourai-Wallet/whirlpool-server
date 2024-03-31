package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.wallet.dexConfig.DexConfigResponse;
import com.samourai.wallet.dexConfig.SamouraiConfig;
import com.samourai.wallet.util.JSONUtils;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DexConfigController extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  @Deprecated public static final String ENDPOINT_DEXCONFIG = "/rest/dex-config";

  private WhirlpoolServerContext serverContext;
  private DexConfigResponse dexConfigResponse;

  @Autowired
  public DexConfigController(WhirlpoolServerContext serverContext) throws Exception {
    this.serverContext = serverContext;
  }

  @RequestMapping(value = ENDPOINT_DEXCONFIG, method = RequestMethod.GET)
  public DexConfigResponse dexConfig() throws Exception {
    if (dexConfigResponse == null) {
      SamouraiConfig samouraiConfig = new SamouraiConfig();
      String samouraiConfigJson =
          JSONUtils.getInstance().getObjectMapper().writeValueAsString(samouraiConfig);
      String signature = Utils.signMessage(serverContext.getSigningWallet(), samouraiConfigJson);
      dexConfigResponse = new DexConfigResponse(samouraiConfigJson, signature);
    }
    if (log.isTraceEnabled()) {
      log.trace("(<) DEXCONFIG");
    }
    return dexConfigResponse;
  }
}
