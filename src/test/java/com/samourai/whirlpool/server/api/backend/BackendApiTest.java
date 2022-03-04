package com.samourai.whirlpool.server.api.backend;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.http.client.HttpUsage;
import com.samourai.wallet.api.backend.BackendApi;
import com.samourai.wallet.api.backend.BackendServer;
import com.samourai.wallet.api.backend.beans.WalletResponse;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.services.JavaHttpClientService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class BackendApiTest extends AbstractIntegrationTest {
  private static final String VPUB =
      "vpub5YS8pQgZKVbrSn9wtrmydDWmWMjHrxL2mBCZ81BDp7Z2QyCgTLZCrnBprufuoUJaQu1ZeiRvUkvdQTNqV6hS96WbbVZgweFxYR1RXYkBcKt";
  private BackendApi backendApi;

  public BackendApiTest() {}

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    backendApi =
        BackendApi.newBackendApiSamourai(
            new JavaHttpClientService(serverConfig).getHttpClient(HttpUsage.BACKEND),
            BackendServer.TESTNET.getBackendUrlClear());
  }

  @Test
  public void fetchWallet() throws Exception {
    WalletResponse walletResponse = backendApi.fetchWallet(VPUB);
    Assertions.assertEquals(VPUB, walletResponse.addresses[0].address);
  }
}
