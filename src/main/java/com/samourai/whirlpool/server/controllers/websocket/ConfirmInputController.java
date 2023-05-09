package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.ConfirmInputRequest;
import com.samourai.whirlpool.server.beans.FailMode;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.ConfirmInputService;
import com.samourai.whirlpool.server.services.ExportService;
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
public class ConfirmInputController extends AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private ConfirmInputService confirmInputService;
  private WhirlpoolServerConfig serverConfig;

  @Autowired
  public ConfirmInputController(
      WSMessageService WSMessageService,
      ExportService exportService,
      ConfirmInputService confirmInputService,
      WhirlpoolServerConfig serverConfig) {
    super(WSMessageService, exportService);
    this.confirmInputService = confirmInputService;
    this.serverConfig = serverConfig;
  }

  @MessageMapping(WhirlpoolEndpoint.WS_CONFIRM_INPUT)
  public void confirmInput(
      @Payload ConfirmInputRequest payload,
      Principal principal,
      StompHeaderAccessor headers,
      SimpMessageHeaderAccessor messageHeaderAccessor)
      throws Exception {
    validateHeaders(headers);

    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.debug(
          "(<) [" + payload.mixId + "] " + headers.getDestination() + ", username=" + username);
    }

    // failMode
    serverConfig.checkFailMode(FailMode.CONFIRM_INPUT);

    // confirm input and send back signed bordereau, or enqueue back to pool
    byte[] blindedBordereau = WhirlpoolProtocol.decodeBytes(payload.blindedBordereau64);
    confirmInputService.confirmInputOrQueuePool(
        payload.mixId, username, blindedBordereau, payload.userHash);
  }

  @MessageExceptionHandler
  public void handleException(
      Exception exception, Principal principal, SimpMessageHeaderAccessor messageHeaderAccessor) {
    super.handleException(exception, principal, messageHeaderAccessor, "CONFIRM_INPUT:ERROR");
  }
}
