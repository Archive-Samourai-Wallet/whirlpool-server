package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.v0.WhirlpoolEndpointV0;
import com.samourai.whirlpool.protocol.websocket.messages.RevealOutputRequest;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.MixStatus;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.services.*;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class RevealOutputController extends AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private RevealOutputService revealOutputService;
  private MixService mixService;
  private MetricService metricService;

  @Autowired
  public RevealOutputController(
      WSMessageService WSMessageService,
      ExportService exportService,
      RevealOutputService revealOutputService,
      MixService mixService,
      MetricService metricService) {
    super(WSMessageService, exportService);
    this.revealOutputService = revealOutputService;
    this.mixService = mixService;
    this.metricService = metricService;
  }

  @MessageMapping(WhirlpoolEndpointV0.WS_REVEAL_OUTPUT)
  public void revealOutput(
      @Payload RevealOutputRequest payload, Principal principal, StompHeaderAccessor headers)
      throws Exception {
    validateHeaders(headers);

    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.debug("(<) MIX_REVEAL_OUTPUT_CLASSIC mixId=" + payload.mixId + " username=" + username);
    }

    // find confirmed input
    Mix mix = mixService.getMix(payload.mixId, MixStatus.REVEAL_OUTPUT);
    Long mixStepElapsedTime = mixService.getMixStepElapsedTime(mix);
    Long mixStepRemainingTime = mixService.getMixStepRemainingTime(mix);
    RegisteredInput confirmedInput =
        mix.getInputs()
            .findByUsername(username)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        WhirlpoolErrorCode.INPUT_REJECTED,
                        "Mix input not found",
                        "username=" + username));

    // revealOutput
    revealOutputService.revealOutput(payload.receiveAddress, mix, confirmedInput);

    metricService.onClientRevealOutput(mix, mixStepElapsedTime, mixStepRemainingTime, false);
  }

  @MessageExceptionHandler
  public void handleException(
      Exception exception, Principal principal, SimpMessageHeaderAccessor messageHeaderAccessor) {
    super.handleException(exception, principal, messageHeaderAccessor, "REVEAL_OUTPUT:ERROR");
  }
}
