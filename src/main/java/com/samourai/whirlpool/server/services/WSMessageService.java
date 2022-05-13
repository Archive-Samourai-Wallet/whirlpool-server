package com.samourai.whirlpool.server.services;

import com.samourai.javawsserver.config.JWSSConfig;
import com.samourai.javawsserver.services.JWSSMessageService;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.ErrorResponse;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WSMessageService extends JWSSMessageService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private WhirlpoolProtocol whirlpoolProtocol;

  @Autowired
  public WSMessageService(
      WhirlpoolProtocol whirlpoolProtocol,
      SimpMessagingTemplate messagingTemplate,
      TaskExecutor taskExecutor,
      JWSSConfig config) {
    super(messagingTemplate, taskExecutor, config);
    this.whirlpoolProtocol = whirlpoolProtocol;
  }

  public void sendPrivateError(String username, int errorCode, String message) {
    if (!RegisterInputService.HEALTH_CHECK_SUCCESS.equals(message)) {
      log.warn("(>) " + username + " sendPrivateError(" + errorCode + "): " + message);
    }
    ErrorResponse errorResponse = new ErrorResponse(errorCode, message);
    sendPrivate(username, errorResponse);
  }

  protected Map<String, Object> computeHeaders(Object payload) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(whirlpoolProtocol.HEADER_MESSAGE_TYPE, payload.getClass().getName());
    headers.put(whirlpoolProtocol.HEADER_PROTOCOL_VERSION, WhirlpoolProtocol.PROTOCOL_VERSION);
    return headers;
  }
}
