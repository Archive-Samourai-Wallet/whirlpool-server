package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.websocket.messages.RegisterInputRequest;
import com.samourai.whirlpool.server.beans.FailMode;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.AlreadyRegisteredInputException;
import com.samourai.whirlpool.server.services.ExportService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.services.WSMessageService;
import com.samourai.whirlpool.server.utils.Utils;
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
public class RegisterInputController extends AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private RegisterInputService registerInputService;
  private WhirlpoolServerConfig serverConfig;

  @Autowired
  public RegisterInputController(
      WSMessageService WSMessageService,
      ExportService exportService,
      RegisterInputService registerInputService,
      WhirlpoolServerConfig serverConfig) {
    super(WSMessageService, exportService);
    this.registerInputService = registerInputService;
    this.serverConfig = serverConfig;
  }

  /** Register inputs for non-soroban clients */
  @MessageMapping(WhirlpoolEndpoint.WS_REGISTER_INPUT)
  @Deprecated
  public void registerInput(
      @Payload RegisterInputRequest payload,
      Principal principal,
      StompHeaderAccessor headers,
      SimpMessageHeaderAccessor messageHeaderAccessor)
      throws Exception {
    validateHeaders(headers);

    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.debug(
          "(<) ["
              + payload.poolId
              + "] "
              + headers.getDestination()
              + " "
              + payload.utxoHash
              + ":"
              + payload.utxoIndex
              + ", username="
              + username);
    }

    // failMode
    serverConfig.checkFailMode(FailMode.REGISTER_INPUT);

    // register input in pool
    Boolean tor = Utils.getTor(messageHeaderAccessor);
    try {
      registerInputService.registerInput(
          payload.poolId,
          username,
          payload.signature,
          payload.utxoHash,
          payload.utxoIndex,
          payload.liquidity,
          tor,
          payload.blockHeight,
          null,
          null,
          computeClientDetails(messageHeaderAccessor));
    } catch (AlreadyRegisteredInputException e) {
      // silent error
      log.warn(e.getMessage());
    }
  }

  @MessageExceptionHandler
  public void handleException(
      Exception exception, Principal principal, SimpMessageHeaderAccessor messageHeaderAccessor) {
    super.handleException(exception, principal, messageHeaderAccessor, "REGISTER_INPUT:ERROR");
  }
}
