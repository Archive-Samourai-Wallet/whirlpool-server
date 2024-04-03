package com.samourai.whirlpool.server.services.soroban;

import com.samourai.whirlpool.protocol.soroban.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.orchestrators.SorobanCoordinatorOrchestrator;
import com.samourai.whirlpool.server.orchestrators.SorobanUpStatusOrchestrator;
import com.samourai.whirlpool.server.services.MetricService;
import com.samourai.whirlpool.server.services.MinerFeeService;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.monitoring.MonitoringService;
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
      MinerFeeService minerFeeService,
      MetricService metricService,
      MonitoringService monitoringService)
      throws Exception {

    // start publishing pools
    coordinatorOrchestrator =
        new SorobanCoordinatorOrchestrator(
            serverConfig, poolService, minerFeeService, whirlpoolApiCoordinator);
    coordinatorOrchestrator.start(true);

    // start watching soroban nodes
    sorobanUpStatusOrchestrator =
        new SorobanUpStatusOrchestrator(serverConfig, whirlpoolApiCoordinator, monitoringService);
    sorobanUpStatusOrchestrator.start(true);

    // fix dependency loop
    metricService.init(sorobanUpStatusOrchestrator);
  }

  public void stop() {
    coordinatorOrchestrator.stop();
  }

  public SorobanCoordinatorOrchestrator _getCoordinatorOrchestrator() { // for tests
    return coordinatorOrchestrator;
  }

  public SorobanUpStatusOrchestrator _getSorobanUpStatusOrchestrator() {
    return sorobanUpStatusOrchestrator;
  }
}
