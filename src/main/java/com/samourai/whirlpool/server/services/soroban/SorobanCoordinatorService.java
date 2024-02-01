package com.samourai.whirlpool.server.services.soroban;

import com.samourai.whirlpool.protocol.soroban.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.orchestrators.SorobanCoordinatorOrchestrator;
import com.samourai.whirlpool.server.orchestrators.SorobanUpStatusOrchestrator;
import com.samourai.whirlpool.server.services.MinerFeeService;
import com.samourai.whirlpool.server.services.PoolService;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SorobanCoordinatorService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private SorobanCoordinatorOrchestrator coordinatorOrchestrator;
  private SorobanUpStatusOrchestrator sorobanUpStatusOrchestrator;

  @Autowired
  public SorobanCoordinatorService(
      PoolService poolService,
      WhirlpoolServerConfig serverConfig,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator,
      MinerFeeService minerFeeService) {

    // start publishing pools
    coordinatorOrchestrator =
        new SorobanCoordinatorOrchestrator(
            serverConfig, poolService, minerFeeService, whirlpoolApiCoordinator);
    coordinatorOrchestrator.start(true);

    // start watching soroban nodes
    sorobanUpStatusOrchestrator =
        new SorobanUpStatusOrchestrator(serverConfig, whirlpoolApiCoordinator);
    sorobanUpStatusOrchestrator.start(true);
  }

  public void stop() {
    coordinatorOrchestrator.stop();
  }

  public SorobanCoordinatorOrchestrator _getCoordinatorOrchestrator() { // for tests
    return coordinatorOrchestrator;
  }
}
