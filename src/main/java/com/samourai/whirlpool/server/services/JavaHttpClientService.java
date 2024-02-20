package com.samourai.whirlpool.server.services;

import com.samourai.http.client.JettyHttpClientService;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import org.springframework.stereotype.Service;

@Service
public class JavaHttpClientService extends JettyHttpClientService {

  public JavaHttpClientService(WhirlpoolServerConfig config) {
    super(config.getRequestTimeout());
  }
}
