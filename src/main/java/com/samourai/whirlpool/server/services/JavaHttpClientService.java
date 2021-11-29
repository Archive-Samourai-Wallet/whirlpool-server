package com.samourai.whirlpool.server.services;

import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClientService;
import com.samourai.http.client.JavaHttpClient;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JavaHttpClientService implements IHttpClientService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WhirlpoolServerConfig config;
  private JavaHttpClient httpClient;

  public JavaHttpClientService(WhirlpoolServerConfig config) {
    this.config = config;
    this.httpClient = null;
  }

  // httpUsage is useless for whirlpool-server
  public JavaHttpClient getHttpClient(HttpUsage httpUsage) {
    if (httpClient == null) {
      if (log.isDebugEnabled()) {
        log.debug("+httpClient");
      }
      httpClient = this.computeHttpClient(httpUsage);
    }

    return httpClient;
  }

  private JavaHttpClient computeHttpClient(HttpUsage httpUsage) {
    return new JavaHttpClient(this.config.getRequestTimeout(), null, httpUsage);
  }

  @Override
  public void stop() {
    if (httpClient != null) {
      httpClient.stop();
    }
  }
}
