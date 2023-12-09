package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.v0.WhirlpoolEndpointV0;
import com.samourai.whirlpool.protocol.v0.rest.CheckOutputRequest;
import com.samourai.whirlpool.protocol.v0.rest.RegisterOutputRequest;
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

  @Autowired
  public RegisterOutputController(RegisterOutputService registerOutputService) {
    this.registerOutputService = registerOutputService;
  }

  @RequestMapping(value = WhirlpoolEndpointV0.REST_CHECK_OUTPUT, method = RequestMethod.POST)
  public void checkOutput(HttpServletRequest request, @RequestBody CheckOutputRequest payload)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) " + WhirlpoolEndpointV0.REST_CHECK_OUTPUT);
    }

    // check output
    registerOutputService.checkOutput(payload.receiveAddress, payload.signature);
  }

  @RequestMapping(value = WhirlpoolEndpointV0.REST_REGISTER_OUTPUT, method = RequestMethod.POST)
  public void registerOutput(HttpServletRequest request, @RequestBody RegisterOutputRequest payload)
      throws Exception {

    // register output
    byte[] unblindedSignedBordereau =
        WhirlpoolProtocol.decodeBytes(payload.unblindedSignedBordereau64);
    byte[] bordereau = WhirlpoolProtocol.decodeBytes(payload.bordereau64);
    if (bordereau == null) {
      // clients < protocol V0.23.9
      bordereau = payload.receiveAddress.getBytes();
    }
    registerOutputService.registerOutput(
        payload.inputsHash, unblindedSignedBordereau, payload.receiveAddress, bordereau);
  }
}
