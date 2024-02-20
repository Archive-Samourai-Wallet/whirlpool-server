package com.samourai.whirlpool.server.services.monitoring;

import com.samourai.wallet.httpClient.HttpUsage;
import com.samourai.wallet.httpClient.IHttpClient;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.JavaHttpClientService;
import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MonitoringService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WhirlpoolServerConfig serverConfig;
  private IHttpClient httpClient;

  @Autowired
  public MonitoringService(
      WhirlpoolServerConfig serverConfig, JavaHttpClientService httpClientService) {
    this.serverConfig = serverConfig;
    this.httpClient = httpClientService.getHttpClient(HttpUsage.BACKEND);
  }

  public void notifySuccess(String info) {
    notify(":white_check_mark: " + info);
  }

  public void notifyInfo(String info) {
    notify(":blue_circle: " + info);
  }

  public void notifyWarning(String info) {
    notify(":warning: " + info);
  }

  public void notifyError(String info) {
    notify(":red_circle: " + info);
  }

  public void notify(String info) {
    String hookUrl = "";
    info = "(" + serverConfig.getCoordinatorName() + ") " + info;
    if (log.isDebugEnabled()) {
      log.debug("notify -> " + info);
    }

    RocketChatPayload payload = new RocketChatPayload(info);
    Map<String, String> headers = new LinkedHashMap<>();
    httpClient.postJson(hookUrl, String.class, headers, payload).subscribe();
  }
}
