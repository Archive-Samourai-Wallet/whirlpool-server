package com.samourai.whirlpool.server.services;

import com.samourai.http.client.IHttpProxySupplier;
import com.samourai.http.client.JettyHttpClientService;
import com.samourai.wallet.httpClient.HttpProxy;
import com.samourai.wallet.httpClient.HttpProxyProtocol;
import com.samourai.wallet.httpClient.HttpUsage;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.utils.Utils;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class JavaHttpClientService extends JettyHttpClientService {

  public JavaHttpClientService(WhirlpoolServerConfig config) {
    super(
        config.getRequestTimeout(),
        new IHttpProxySupplier() {
          @Override
          public Optional<HttpProxy> getHttpProxy(HttpUsage httpUsage) {
            if (Utils.HTTPUSAGE_SOROBAN_ONION.equals(httpUsage)) {
              // use TOR proxy
              return Optional.of(
                  new HttpProxy(
                      HttpProxyProtocol.SOCKS5,
                      config.getTorProxy().getHost(),
                      config.getTorProxy().getPort()));
            }
            return Optional.empty();
          }

          @Override
          public void changeIdentity() {
            // not implemented
          }
        });
  }
}
