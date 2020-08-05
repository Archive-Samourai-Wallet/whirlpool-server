package com.samourai.whirlpool.server.services;

import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClientService;
import com.samourai.http.client.JavaHttpClient;
import com.samourai.whirlpool.cli.utils.CliUtils;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.lang.invoke.MethodHandles;
import org.eclipse.jetty.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JavaHttpClientService implements IHttpClientService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String USER_AGENT = "whirlpool-server " + WhirlpoolProtocol.PROTOCOL_VERSION;

  private WhirlpoolServerConfig config;
  private JavaHttpClient httpClient;

  public JavaHttpClientService(WhirlpoolServerConfig config) {
    this.config = config;
    this.httpClient = null;
  }

  // TODO httpUsage is useless for whirlpool-server
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
    HttpClient httpClient = CliUtils.computeHttpClient(null, USER_AGENT);
    return new JavaHttpClient(httpUsage, httpClient, this.config.getRequestTimeout());
  }
}
