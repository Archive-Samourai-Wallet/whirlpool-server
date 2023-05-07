package com.samourai.whirlpool.server.orchestrators;

import com.samourai.soroban.client.rpc.RpcClientEncrypted;
import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.whirlpool.protocol.rest.PoolInfoSoroban;
import com.samourai.whirlpool.server.services.MinerFeeService;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.soroban.SorobanCoordinatorApi;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SorobanPoolInfoOrchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final int LOOP_DELAY = 30000;
  private static final int START_DELAY = 1000;

  private PoolService poolService;
  private MinerFeeService minerFeeService;
  private SorobanCoordinatorApi sorobanCoordinatorApi;
  private RpcClientEncrypted rpcClient;

  public SorobanPoolInfoOrchestrator(
      PoolService poolService,
      MinerFeeService minerFeeService,
      SorobanCoordinatorApi sorobanCoordinatorApi,
      RpcClientEncrypted rpcClient) {
    super(LOOP_DELAY, START_DELAY, null);
    this.poolService = poolService;
    this.minerFeeService = minerFeeService;
    this.sorobanCoordinatorApi = sorobanCoordinatorApi;
    this.rpcClient = rpcClient;
  }

  @Override
  protected void runOrchestrator() {
    try {
      // register poolInfos
      long mixFeePerB = minerFeeService.getMixFeePerB();
      Collection<PoolInfoSoroban> poolInfosSoroban =
          poolService.computePoolInfosSoroban(mixFeePerB);
      if (log.isDebugEnabled()) {
        log.debug("registering Soroban pools: " + poolInfosSoroban.size() + " pools");
      }
      AsyncUtil.getInstance()
          .blockingAwait(sorobanCoordinatorApi.registerPools(rpcClient, poolInfosSoroban));
    } catch (Exception e) {
      log.error("Failed to register Soroban pools", e);
    }
  }

  public void _runOrchestrator() { // for tests
    runOrchestrator();
  }
}
