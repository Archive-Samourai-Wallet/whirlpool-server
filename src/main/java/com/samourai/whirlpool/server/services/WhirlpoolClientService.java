package com.samourai.whirlpool.server.services;

import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClient;
import com.samourai.http.client.IWhirlpoolHttpClientService;
import com.samourai.stomp.client.IStompClientService;
import com.samourai.stomp.client.JettyStompClientService;
import com.samourai.tor.client.TorClientService;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.IndexRange;
import com.samourai.whirlpool.client.whirlpool.ServerApi;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WhirlpoolClientService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private JavaHttpClientService httpClientService;

  @Autowired
  public WhirlpoolClientService(JavaHttpClientService httpClientService) {
    this.httpClientService = httpClientService;
  }

  public WhirlpoolClientConfig createWhirlpoolClientConfig(
      String serverUrl, NetworkParameters params) {
    TorClientService torClientService =
        new TorClientService() {
          @Override
          public void changeIdentity() {}
        };
    IStompClientService stompClientService =
        new JettyStompClientService(
            httpClientService, WhirlpoolProtocol.HEADER_MESSAGE_TYPE, ClientUtils.USER_AGENT);
    IWhirlpoolHttpClientService multiUsageHttpClientService =
        new IWhirlpoolHttpClientService() {
          @Override
          public IHttpClient getHttpClient(HttpUsage httpUsage) {
            return httpClientService.getHttpClient();
          }

          @Override
          public void stop() {
            httpClientService.stop();
          }
        };

    ServerApi serverApi =
        new ServerApi(
            serverUrl, httpClientService.getHttpClient(), httpClientService.getHttpClient());
    return new WhirlpoolClientConfig(
        multiUsageHttpClientService,
        stompClientService,
        torClientService,
        serverApi,
        null,
        params,
        IndexRange.FULL);
  }
}
