package com.samourai.whirlpool.server.services.soroban;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.whirlpool.client.tx0.ITx0PreviewServiceConfig;
import com.samourai.whirlpool.client.tx0.MockTx0PreviewService;
import com.samourai.whirlpool.client.tx0.MockTx0PreviewServiceConfig;
import com.samourai.whirlpool.client.tx0.Tx0PreviewService;
import com.samourai.whirlpool.client.wallet.data.coordinator.ExpirableCoordinatorSupplier;
import com.samourai.whirlpool.client.wallet.data.minerFee.MockMinerFeeSupplier;
import com.samourai.whirlpool.client.whirlpool.beans.Coordinator;
import com.samourai.whirlpool.protocol.soroban.api.WhirlpoolApiClient;
import com.samourai.whirlpool.server.controllers.soroban.RegisterInputControllerSoroban;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.orchestrators.SorobanCoordinatorOrchestrator;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class SorobanCoordinatorServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private SorobanCoordinatorOrchestrator poolInfoOrchestrator;

  @Autowired private RegisterInputControllerSoroban registerInputControllerSoroban;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true);
    poolInfoOrchestrator = sorobanCoordinatorService._getCoordinatorOrchestrator();
  }

  @Test
  public void poolInfo() throws Exception {
    poolInfoOrchestrator._runOrchestrator();
    poolInfoOrchestrator._runOrchestrator();
    poolInfoOrchestrator._runOrchestrator();

    RpcSession rpcSession = rpcClientServiceServer.generateRpcWallet().createRpcSession();
    WhirlpoolApiClient whirlpoolApiClient =
        new WhirlpoolApiClient(rpcSession, sorobanProtocolWhirlpool);

    // fetch pools from Soroban
    MockMinerFeeSupplier minerFeeSupplier = new MockMinerFeeSupplier();
    ITx0PreviewServiceConfig tx0PreviewServiceConfig =
        new MockTx0PreviewServiceConfig(serverConfig.getWhirlpoolNetwork());
    Tx0PreviewService tx0PreviewService =
        new MockTx0PreviewService(minerFeeSupplier, tx0PreviewServiceConfig);
    ExpirableCoordinatorSupplier coordinatorSupplier =
        new ExpirableCoordinatorSupplier(
            30000, whirlpoolApiClient, serverConfig.getWhirlpoolNetwork(), tx0PreviewService);
    coordinatorSupplier.load();
    Collection<Coordinator> coordinators = coordinatorSupplier.getCoordinators();

    Assertions.assertEquals(1, coordinators.size());
    Coordinator coordinator = coordinators.iterator().next();
    Assertions.assertEquals(serverConfig.getCoordinatorId(), coordinator.getCoordinatorId());
    Assertions.assertEquals(
        serverContext.getCoordinatorWallet().getPaymentCode().toString(),
        coordinator.getPaymentCode().toString());
    Assertions.assertArrayEquals(
        poolService.getPools().stream().map(p -> p.getPoolId()).sorted().toArray(),
        coordinator.getPoolIds().stream().sorted().toArray());
  }
}
