package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.v0.WhirlpoolEndpointV0;
import com.samourai.whirlpool.protocol.websocket.messages.RevealOutputRequest;
import com.samourai.whirlpool.server.services.ExportService;
import com.samourai.whirlpool.server.services.RevealOutputService;
import com.samourai.whirlpool.server.services.WSMessageService;
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

  @Autowired
  public RevealOutputController(
      WSMessageService WSMessageService,
      ExportService exportService,
      RevealOutputService revealOutputService) {
    super(WSMessageService, exportService);
    this.revealOutputService = revealOutputService;
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
    revealOutputService.revealOutput_webSocket(payload.mixId, payload.receiveAddress, username);
  }

  @MessageExceptionHandler
  public void handleException(
      Exception exception, Principal principal, SimpMessageHeaderAccessor messageHeaderAccessor) {
    super.handleException(exception, principal, messageHeaderAccessor, "REVEAL_OUTPUT:ERROR");
  }
}
