package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.v0.WhirlpoolEndpointV0;
import com.samourai.whirlpool.protocol.v0.rest.CheckOutputRequest;
import com.samourai.whirlpool.protocol.v0.rest.RegisterOutputRequest;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.MixStatus;
import com.samourai.whirlpool.server.services.MixService;
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

  private MixService mixService;
  private RegisterOutputService registerOutputService;

  @Autowired
  public RegisterOutputController(
      MixService mixService, RegisterOutputService registerOutputService) {
    this.mixService = mixService;
    this.registerOutputService = registerOutputService;
  }

  @RequestMapping(value = WhirlpoolEndpointV0.REST_CHECK_OUTPUT, method = RequestMethod.POST)
  public void checkOutput(HttpServletRequest request, @RequestBody CheckOutputRequest payload)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) CHECK_OUTPUT " + WhirlpoolEndpointV0.REST_CHECK_OUTPUT);
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

    // find mix
    Mix mix;
    try {
      mix = mixService.getMixByInputsHash(payload.inputsHash, MixStatus.REGISTER_OUTPUT);
      if (log.isDebugEnabled()) {
        log.debug("(<) REGISTER_OUTPUT_CLASSIC " + mix.getMixId() + " " + payload.receiveAddress);
      }
    } catch (Exception e) {
      // mix not found for inputsHash
      log.warn(
          "(<) REGISTER_OUTPUT_CLASSIC ERROR_MIX_OVER inputsHash="
              + payload.inputsHash
              + " "
              + payload.receiveAddress);
      throw new NotifiableException(WhirlpoolErrorCode.MIX_OVER, "Mix over");
    }

    registerOutputService.registerOutput(
        mix, unblindedSignedBordereau, payload.receiveAddress, bordereau);
  }
}
