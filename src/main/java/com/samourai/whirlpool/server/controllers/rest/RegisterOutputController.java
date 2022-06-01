package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.CheckOutputRequest;
import com.samourai.whirlpool.protocol.rest.RegisterOutputRequest;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.ExportService;
import com.samourai.whirlpool.server.services.RegisterOutputService;
import java.lang.invoke.MethodHandles;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegisterOutputController extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private RegisterOutputService registerOutputService;
  private ExportService exportService;
  private WhirlpoolServerConfig serverConfig;

  @Autowired
  public RegisterOutputController(
      RegisterOutputService registerOutputService,
      ExportService exportService,
      WhirlpoolServerConfig serverConfig) {
    this.registerOutputService = registerOutputService;
    this.exportService = exportService;
    this.serverConfig = serverConfig;
  }

  @RequestMapping(value = WhirlpoolEndpoint.REST_CHECK_OUTPUT, method = RequestMethod.POST)
  public void checkOutput(HttpServletRequest request, @RequestBody CheckOutputRequest payload)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) " + WhirlpoolEndpoint.REST_CHECK_OUTPUT);
    }

    // check output
    registerOutputService.checkOutput(payload.receiveAddress, payload.signature);
  }

  @RequestMapping(value = WhirlpoolEndpoint.REST_REGISTER_OUTPUT, method = RequestMethod.POST)
  public void registerOutput(HttpServletRequest request, @RequestBody RegisterOutputRequest payload)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) " + WhirlpoolEndpoint.REST_REGISTER_OUTPUT);
    }

    // register output
    byte[] unblindedSignedBordereau =
        WhirlpoolProtocol.decodeBytes(payload.unblindedSignedBordereau64);
    byte[] bordereau = WhirlpoolProtocol.decodeBytes(payload.bordereau64);
    if (bordereau == null) {
      // clients < protocol V0.23.9
      bordereau = payload.receiveAddress.getBytes();
    }
    Mix mix =
        registerOutputService.registerOutput(
            payload.inputsHash, unblindedSignedBordereau, payload.receiveAddress, bordereau);

    // log activity
    String poolId = mix.getPool().getPoolId();
    ActivityCsv activityCsv = new ActivityCsv("REGISTER_OUTPUT", poolId, null, null, request);
    exportService.exportActivity(activityCsv);
  }
}
