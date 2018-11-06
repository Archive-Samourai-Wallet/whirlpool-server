package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.SubscribePoolResponse;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.WebSocketService;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class SubscribePoolController extends AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final int SUBSCRIBE_RESPONSE_DELAY = 1000;

  private PoolService poolService;
  private TaskExecutor taskExecutor;

  @Autowired
  public SubscribePoolController(
      PoolService poolService, WebSocketService webSocketService, TaskExecutor taskExecutor) {
    super(webSocketService);
    this.poolService = poolService;
    this.taskExecutor = taskExecutor;
  }

  @SubscribeMapping(
      WhirlpoolProtocol.SOCKET_SUBSCRIBE_USER_PRIVATE
          + WhirlpoolProtocol.SOCKET_SUBSCRIBE_USER_REPLY)
  public void subscribePool(Principal principal, StompHeaderAccessor headers) throws Exception {
    // don't validate headers here, so user is able to receive protocol version mismatch errors

    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.info("[controller] subscribe:" + headers.getDestination() + ": username=" + username);
    }

    // validate poolId & reply poolStatusNotification
    String headerPoolId = getHeaderPoolId(headers);
    SubscribePoolResponse subscribePoolResponse =
        poolService.computeSubscribePoolResponse(headerPoolId);

    // delay to make sure client processed subscription before sending him private response
    taskExecutor.execute(
        () -> {
          synchronized (this) {
            try {
              wait(SUBSCRIBE_RESPONSE_DELAY);
            } catch (InterruptedException e) {
            }
          }
          // send reply
          getWebSocketService().sendPrivate(username, subscribePoolResponse);
        });
  }

  private String getHeaderPoolId(StompHeaderAccessor headers) {
    return headers.getFirstNativeHeader(WhirlpoolProtocol.HEADER_POOL_ID);
  }

  @MessageExceptionHandler
  public void handleException(Exception exception, Principal principal) {
    super.handleException(exception, principal);
  }
}
