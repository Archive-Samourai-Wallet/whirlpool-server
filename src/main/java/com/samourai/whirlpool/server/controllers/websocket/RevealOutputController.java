package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.RevealOutputRequest;
import com.samourai.whirlpool.server.services.MixService;
import com.samourai.whirlpool.server.services.WebSocketService;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class RevealOutputController extends AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MixService mixService;

  @Autowired
  public RevealOutputController(WebSocketService webSocketService, MixService mixService) {
    super(webSocketService);
    this.mixService = mixService;
  }

  @MessageMapping(WhirlpoolProtocol.ENDPOINT_REVEAL_OUTPUT)
  public void revealOutput(
      @Payload RevealOutputRequest payload, Principal principal, StompHeaderAccessor headers)
      throws Exception {
    validateHeaders(headers);

    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.debug(
          "[controller] "
              + headers.getDestination()
              + ": username="
              + username
              + ", payload="
              + Utils.toJsonString(payload));
    }

    // register output
    mixService.revealOutput(payload.mixId, username, payload.receiveAddress);
  }

  @MessageExceptionHandler
  public void handleException(Exception exception, Principal principal) {
    super.handleException(exception, principal);
  }
}
