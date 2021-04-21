package com.samourai.whirlpool.server.config.security;

import com.samourai.javawsserver.config.JWSSConfig;
import com.samourai.javawsserver.config.JWSSWebSocketConfigurationSupport;
import com.samourai.whirlpool.server.services.WSSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WSConfigurationSupport extends JWSSWebSocketConfigurationSupport {

  @Autowired
  public WSConfigurationSupport(JWSSConfig config, WSSessionService sessionService) {
    super(config, sessionService);
  }
}
