package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.server.controllers.rest.beans.SamouraiConfig;
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

  @Autowired
  public DexConfigController() {}

  @RequestMapping(value = ENDPOINT_DEXCONFIG, method = RequestMethod.GET)
  public SamouraiConfig dexConfig(HttpServletRequest request) throws Exception {
    return new SamouraiConfig(); // TODO upgrade ExtLibJ
  }
}
