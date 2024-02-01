package com.samourai.whirlpool.server.orchestrators;

import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.whirlpool.protocol.soroban.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.protocol.soroban.payload.coordinators.CoordinatorInfo;
import com.samourai.whirlpool.protocol.soroban.payload.coordinators.CoordinatorMessage;
import com.samourai.whirlpool.protocol.soroban.payload.coordinators.PoolInfo;
import com.samourai.whirlpool.protocol.soroban.payload.coordinators.SorobanInfo;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.MinerFeeService;
import com.samourai.whirlpool.server.services.PoolService;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SorobanCoordinatorOrchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final long COORDINATOR_PRIORITY = 10; // TODO
  public static final int REGISTER_COORDINATOR_FREQUENCY_MS =
      60000; // getEndpointCoordinator.getExpirationMs()/3
  public static final int START_DELAY_MS = 5000; // wait server successfully started

  private WhirlpoolServerConfig serverConfig;
  private PoolService poolService;
  private MinerFeeService minerFeeService;
  private WhirlpoolApiCoordinator whirlpoolApiCoordinator;

  public SorobanCoordinatorOrchestrator(
      WhirlpoolServerConfig serverConfig,
      PoolService poolService,
      MinerFeeService minerFeeService,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator) {

    super(REGISTER_COORDINATOR_FREQUENCY_MS, START_DELAY_MS, null);
    this.serverConfig = serverConfig;
    this.poolService = poolService;
    this.minerFeeService = minerFeeService;
    this.whirlpoolApiCoordinator = whirlpoolApiCoordinator;
  }

  @Override
  protected void runOrchestrator() {
    try {
      // register coordinator
      long mixFeePerB = minerFeeService.getMixFeePerB();
      Collection<PoolInfo> poolInfosSoroban = poolService.computePoolInfosSoroban(mixFeePerB);
      if (log.isDebugEnabled()) {
        log.debug("registering coordinator: " + poolInfosSoroban.size() + " pools");
      }
      CoordinatorInfo coordinatorInfo =
          new CoordinatorInfo(serverConfig.getCoordinatorName(), COORDINATOR_PRIORITY);
      SorobanInfo sorobanInfo =
          new SorobanInfo(
              whirlpoolApiCoordinator.getRpcSession().getServerUrlsUp(false),
              whirlpoolApiCoordinator.getRpcSession().getServerUrlsUp(true));
      CoordinatorMessage coordinatorMessage =
          new CoordinatorMessage(coordinatorInfo, poolInfosSoroban, sorobanInfo);
      if (log.isDebugEnabled()) {
        log.debug("CoordinatorMessage = " + coordinatorMessage.toPayload());
      }
      AsyncUtil.getInstance()
          .blockingAwait(whirlpoolApiCoordinator.coordinatorsRegister(coordinatorMessage));
    } catch (Exception e) {
      log.error("Failed to register Soroban pools", e);
    }
  }

  public void _runOrchestrator() { // for tests
    runOrchestrator();
  }
}
