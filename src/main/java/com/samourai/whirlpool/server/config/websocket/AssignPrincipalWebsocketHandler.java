package com.samourai.whirlpool.server.config.websocket;

import com.samourai.whirlpool.server.utils.Utils;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * Assign a random username as principal for each websocket client. This is needed to be able to
 * communicate with a specific client.
 */
public class AssignPrincipalWebsocketHandler extends DefaultHandshakeHandler {
  private static final String ATTR_PRINCIPAL = "__principal__";

  @Override
  protected Principal determineUser(
      ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
    final String name;
    if (!attributes.containsKey(ATTR_PRINCIPAL)) {
      name = generateRandomUsername();
      attributes.put(ATTR_PRINCIPAL, name);
    } else {
      name = (String) attributes.get(ATTR_PRINCIPAL);
    }
    return () -> name;
  }

  private String generateRandomUsername() {
    return Utils.generateUniqueString();
  }
}
