package com.samourai.whirlpool.server.orchestrators;

import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.whirlpool.protocol.soroban.RegisterCoordinatorMessage;
import com.samourai.whirlpool.protocol.soroban.api.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.protocol.soroban.beans.CoordinatorInfo;
import com.samourai.whirlpool.protocol.soroban.beans.PoolInfo;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.services.MinerFeeService;
import com.samourai.whirlpool.server.services.PoolService;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SorobanCoordinatorOrchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final int LOOP_DELAY = 30000;

  private WhirlpoolServerConfig serverConfig;
  private WhirlpoolServerContext serverContext;
  private PoolService poolService;
  private MinerFeeService minerFeeService;
  private WhirlpoolApiCoordinator whirlpoolApiCoordinator;

  public SorobanCoordinatorOrchestrator(
      WhirlpoolServerConfig serverConfig,
      WhirlpoolServerContext serverContext,
      PoolService poolService,
      MinerFeeService minerFeeService,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator) {

    super(LOOP_DELAY, 0, null);
    this.serverConfig = serverConfig;
    this.serverContext = serverContext;
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
          new CoordinatorInfo(
              serverConfig.getCoordinatorId(),
              serverContext.getCoordinatorWallet().getPaymentCode().toString(),
              serverContext.getCoordinatorWalletPaymentCodeSignature());
      RegisterCoordinatorMessage registerCoordinatorMessage =
          new RegisterCoordinatorMessage(coordinatorInfo, poolInfosSoroban);
      if (log.isDebugEnabled()) {
        log.debug("RegisterCoordinatorMessage = " + registerCoordinatorMessage.toPayload());
      }
      AsyncUtil.getInstance()
          .blockingAwait(whirlpoolApiCoordinator.registerCoordinator(registerCoordinatorMessage));
    } catch (Exception e) {
      log.error("Failed to register Soroban pools", e);
    }
  }

  public void _runOrchestrator() { // for tests
    runOrchestrator();
  }
}
