package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.dex.config.DexConfigResponse;
import com.samourai.dex.config.SamouraiConfig;
import com.samourai.wallet.util.JSONUtils;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DexConfigController extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT_DEXCONFIG = "/rest/dex-config";

  private WhirlpoolServerConfig serverConfig;
  private DexConfigResponse dexConfigResponse;

  @Autowired
  public DexConfigController(WhirlpoolServerConfig serverConfig) throws Exception {
    this.serverConfig = serverConfig;
  }

  @RequestMapping(value = ENDPOINT_DEXCONFIG, method = RequestMethod.GET)
  public DexConfigResponse dexConfig(HttpServletRequest request) throws Exception {
    if (dexConfigResponse == null) {
      SamouraiConfig samouraiConfig = new SamouraiConfig();
      // TODO temporary
      samouraiConfig.setSorobanServerTestnetClear(
          samouraiConfig.getSorobanServerTestnetClear() + "?whirlpoolServer=true");
      String samouraiConfigJson =
          JSONUtils.getInstance().getObjectMapper().writeValueAsString(samouraiConfig);
      String signature =
          Utils.sign(
              serverConfig.getDexConfigWallet(),
              serverConfig.getNetworkParameters(),
              samouraiConfigJson);
      dexConfigResponse = new DexConfigResponse(samouraiConfigJson, signature);
    }
    return dexConfigResponse;
  }
}