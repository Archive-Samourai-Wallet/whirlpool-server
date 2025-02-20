package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.v0.WhirlpoolEndpointV0;
import com.samourai.whirlpool.protocol.websocket.messages.SigningRequest;
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
public class SigningController extends AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private SigningService signingService;
  private MixService mixService;
  private MetricService metricService;

  @Autowired
  public SigningController(
      WSMessageService WSMessageService,
      ExportService exportService,
      SigningService signingService,
      MixService mixService,
      MetricService metricService) {
    super(WSMessageService, exportService);
    this.signingService = signingService;
    this.mixService = mixService;
    this.metricService = metricService;
  }

  @MessageMapping(WhirlpoolEndpointV0.WS_SIGNING)
  public void signing(
      @Payload SigningRequest payload, Principal principal, StompHeaderAccessor headers)
      throws Exception {
    validateHeaders(headers);
    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.debug("(<) MIX_SIGNING_CLASSIC mixId=" + payload.mixId + " username=" + username);
    }

    // find confirmed input
    Mix mix = mixService.getMix(payload.mixId, MixStatus.SIGNING);
    RegisteredInput confirmedInput =
        mix.getInputs()
            .findByUsername(username)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        WhirlpoolErrorCode.INPUT_REJECTED,
                        "Mix input not found",
                        "username=" + username));

    Long mixStepElapsedTime = mixService.getMixStepElapsedTime(mix);
    Long mixStepRemainingTime = mixService.getMixStepRemainingTime(mix);

    // signing
    signingService.signing(payload.witnesses64, mix, confirmedInput);

    metricService.onClientSigning(mix, mixStepElapsedTime, mixStepRemainingTime, false);
  }

  @MessageExceptionHandler
  public void handleException(
      Exception exception, Principal principal, SimpMessageHeaderAccessor messageHeaderAccessor) {
    super.handleException(exception, principal, messageHeaderAccessor, "SIGNING:ERROR");
  }
}
