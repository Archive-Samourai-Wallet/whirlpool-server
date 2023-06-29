package com.samourai.whirlpool.server.services;

import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClient;
import com.samourai.http.client.IHttpClientService;
import com.samourai.http.client.JettyHttpClient;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JavaHttpClientService implements IHttpClientService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WhirlpoolServerConfig config;
  private JettyHttpClient httpClient;

  public JavaHttpClientService(WhirlpoolServerConfig config) {
    this.config = config;
    this.httpClient = null;
  }

  public IHttpClient getHttpClient() {
    if (httpClient == null) {
      if (log.isDebugEnabled()) {
        log.debug("+httpClient");
      }
      httpClient = this.computeHttpClient();
    }
    return httpClient;
  }

  @Override
  public IHttpClient getHttpClient(HttpUsage httpUsage) {
    // ignore httpUsage for whirlpool-server
    return getHttpClient();
  }

  private JettyHttpClient computeHttpClient() {
    return new JettyHttpClient(this.config.getRequestTimeout(), null, ClientUtils.USER_AGENT);
  }

  @Override
  public void stop() {
    if (httpClient != null) {
      httpClient.stop();
    }
  }
}
