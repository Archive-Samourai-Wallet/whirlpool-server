package com.samourai.whirlpool.server.orchestrators;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.soroban.client.rpc.RpcClient;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.whirlpool.client.soroban.SorobanClientApi;
import com.samourai.whirlpool.protocol.rest.PoolInfoSoroban;
import com.samourai.whirlpool.protocol.soroban.PoolInfoSorobanMessage;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class SorobanPoolInfoOrchestratorTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void poolInfo() throws Exception {
    RpcClient rpcClient = rpcClientServiceServer.getRpcClient("test");
    SorobanClientApi sorobanClientApi = new SorobanClientApi();

    // fetch pools from Soroban
    Collection<PoolInfoSorobanMessage> poolInfoSorobanMessages =
        AsyncUtil.getInstance().blockingGet(sorobanClientApi.fetchPools(rpcClient));

    Assertions.assertEquals(1, poolInfoSorobanMessages.size());
    Collection<PoolInfoSoroban> poolInfoSorobans =
        poolInfoSorobanMessages.iterator().next().poolInfo;
    Assertions.assertEquals(4, poolInfoSorobans.size());
  }
}
