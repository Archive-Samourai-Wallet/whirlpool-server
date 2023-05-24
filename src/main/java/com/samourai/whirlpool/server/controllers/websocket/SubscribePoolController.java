package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.SubscribePoolResponse;
import com.samourai.whirlpool.server.services.ExportService;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.TaskService;
import com.samourai.whirlpool.server.services.WSMessageService;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class SubscribePoolController extends AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final int SUBSCRIBE_RESPONSE_DELAY = 1000;

  private PoolService poolService;
  private TaskService taskService;

  @Autowired
  public SubscribePoolController(
      PoolService poolService,
      ExportService exportService,
      WSMessageService WSMessageService,
      TaskService taskService) {
    super(WSMessageService, exportService);
    this.poolService = poolService;
    this.taskService = taskService;
  }

  @SubscribeMapping(
      WhirlpoolProtocol.WS_PREFIX_USER_PRIVATE + WhirlpoolProtocol.WS_PREFIX_USER_REPLY)
  public void subscribePool(Principal principal, StompHeaderAccessor headers) throws Exception {
    // don't validate headers here, so user is able to receive protocol version mismatch errors

    String username = principal.getName();
    if (log.isTraceEnabled()) {
      log.trace("(<) subscribe " + headers.getDestination() + ", username=" + username);
    }

    // validate poolId & reply poolStatusNotification
    String headerPoolId = getHeaderPoolId(headers);
    if (headerPoolId != null) {
      // reply pool info for older non-soroban clients
      SubscribePoolResponse subscribePoolResponse =
          poolService.computeSubscribePoolResponse(headerPoolId);

      // delay to make sure client processed subscription before sending him private response
      taskService.runOnce(
          SUBSCRIBE_RESPONSE_DELAY,
          () -> {
            // send reply
            getWSMessageService().sendPrivate(username, subscribePoolResponse);
          });
    }
  }

  private String getHeaderPoolId(StompHeaderAccessor headers) {
    return headers.getFirstNativeHeader(WhirlpoolProtocol.HEADER_POOL_ID);
  }

  @MessageExceptionHandler
  public void handleException(
      Exception exception, Principal principal, SimpMessageHeaderAccessor messageHeaderAccessor) {
    super.handleException(exception, principal, messageHeaderAccessor, "SUBSCRIBE:ERROR");
  }
}
