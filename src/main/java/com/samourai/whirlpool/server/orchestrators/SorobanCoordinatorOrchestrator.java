package com.samourai.whirlpool.server.orchestrators;

import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.whirlpool.protocol.rest.PoolInfoSoroban;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.services.MinerFeeService;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.soroban.SorobanCoordinatorApi;
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
  private SorobanCoordinatorApi sorobanCoordinatorApi;
  private RpcSession rpcSession;

  public SorobanCoordinatorOrchestrator(
      WhirlpoolServerConfig serverConfig,
      WhirlpoolServerContext serverContext,
      PoolService poolService,
      MinerFeeService minerFeeService,
      SorobanCoordinatorApi sorobanCoordinatorApi,
      RpcSession rpcSession) {
    super(LOOP_DELAY, 0, null);
    this.serverConfig = serverConfig;
    this.serverContext = serverContext;
    this.poolService = poolService;
    this.minerFeeService = minerFeeService;
    this.sorobanCoordinatorApi = sorobanCoordinatorApi;
    this.rpcSession = rpcSession;
  }

  @Override
  protected void runOrchestrator() {
    try {
      // register coordinator
      long mixFeePerB = minerFeeService.getMixFeePerB();
      Collection<PoolInfoSoroban> poolInfosSoroban =
          poolService.computePoolInfosSoroban(mixFeePerB);
      if (log.isDebugEnabled()) {
        log.debug("registering coordinator: " + poolInfosSoroban.size() + " pools");
      }
      AsyncUtil.getInstance()
          .blockingAwait(
              rpcSession.withSorobanClient(
                  sorobanClient ->
                      sorobanCoordinatorApi.registerCoordinator(
                          sorobanClient,
                          serverConfig.getCoordinatorId(),
                          serverContext.getCoordinatorWallet().getPaymentCode(),
                          serverContext.getCoordinatorWalletPaymentCodeSignature(),
                          serverConfig.getExternalUrlClear(),
                          serverConfig.getExternalUrlOnion(),
                          poolInfosSoroban)));
    } catch (Exception e) {
      log.error("Failed to register Soroban pools", e);
    }
  }

  public void _runOrchestrator() { // for tests
    runOrchestrator();
  }
}
