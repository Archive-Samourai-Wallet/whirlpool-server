package com.samourai.whirlpool.server.services;

import com.google.common.collect.ImmutableMap;
import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.soroban.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.protocol.soroban.payload.coordinators.PoolInfo;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class PoolService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WhirlpoolServerConfig whirlpoolServerConfig;
  private ExportService exportService;
  private MetricService metricService;
  private WhirlpoolApiCoordinator whirlpoolApiCoordinator;
  private Map<String, Pool> pools;

  @Autowired
  public PoolService(
      WhirlpoolServerConfig whirlpoolServerConfig,
      ExportService exportService,
      MetricService metricService,
      WSSessionService wsSessionService,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator) {
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.exportService = exportService;
    this.metricService = metricService;
    this.whirlpoolApiCoordinator = whirlpoolApiCoordinator;
    __reset();

    // listen websocket onDisconnect
    wsSessionService.addOnDisconnectListener(username -> onClientDisconnect(username));
  }

  public void __reset() {
    WhirlpoolServerConfig.PoolConfig[] poolConfigs = whirlpoolServerConfig.getPools();
    WhirlpoolServerConfig.PoolMinerFeeConfig globalMinerFeeConfig =
        whirlpoolServerConfig.getMinerFees();
    __reset(poolConfigs, globalMinerFeeConfig);
  }

  public void __reset(
      WhirlpoolServerConfig.PoolConfig[] poolConfigs,
      WhirlpoolServerConfig.PoolMinerFeeConfig globalMinerFeeConfig) {
    pools = new ConcurrentHashMap<>();
    for (WhirlpoolServerConfig.PoolConfig poolConfig : poolConfigs) {
      PoolMinerFee minerFee = new PoolMinerFee(globalMinerFeeConfig, poolConfig.getMinerFees());
      __reset(poolConfig, minerFee);
    }
  }

  public Pool __reset(WhirlpoolServerConfig.PoolConfig poolConfig, PoolMinerFee minerFee) {
    String poolId = poolConfig.getId();

    Assert.notNull(poolId, "Pool configuration: poolId must not be NULL");
    PoolFee poolFee = new PoolFee(poolConfig.getFeeValue(), poolConfig.getFeeAccept());
    Pool pool =
        new Pool(
            poolId,
            poolConfig.getDenomination(),
            poolFee,
            poolConfig.getMustMixMin(),
            poolConfig.getLiquidityMin(),
            poolConfig.getSurge(),
            poolConfig.getMinLiquidityPoolForSurge(),
            poolConfig.getAnonymitySet(),
            poolConfig.getTx0MaxOutputs(),
            minerFee,
            whirlpoolApiCoordinator);
    pools.put(poolId, pool);
    metricService.manage(pool);
    return pool;
  }

  public Collection<Pool> getPools() {
    return pools.values();
  }

  public Collection<PoolInfo> computePoolInfosSoroban(long feePerB) {
    return getPools().parallelStream()
        .map(
            pool ->
                new PoolInfo(
                    pool.getPoolId(),
                    pool.getDenomination(),
                    pool.getPoolFee().getFeeValue(),
                    pool.computePremixValue(feePerB),
                    pool.computeMustMixBalanceMin(),
                    pool.computeMustMixBalanceCap(), // provide virtual cap as max
                    pool.getTx0MaxOutputs(),
                    pool.getAnonymitySet()))
        .collect(Collectors.toList());
  }

  public Optional<Pool> findByInputValue(long inputValue, boolean liquidity) {
    Comparator<Pool> comparatorPoolsByDenominationDesc =
        Comparator.comparing(Pool::getDenomination).reversed();
    return pools.values().stream()
        .sorted(comparatorPoolsByDenominationDesc)
        .filter(pool -> pool.checkInputBalance(inputValue, liquidity))
        .findFirst();
  }

  public Pool getPool(String poolId) throws IllegalInputException {
    Pool pool = pools.get(poolId);
    if (pool == null) {
      throw new IllegalInputException(WhirlpoolErrorCode.INVALID_ARGUMENT, "Pool not found");
    }
    return pool;
  }

  public void registerInput(RegisteredInput registeredInput, Map<String, String> clientDetails)
      throws NotifiableException {
    // queue input
    getPoolQueue(registeredInput).register(registeredInput);
    if (log.isDebugEnabled()) {
      log.debug("+INPUT_QUEUE_CLASSIC " + registeredInput.getPoolId() + " " + registeredInput);
    }

    // log activity
    if (clientDetails == null) {
      clientDetails = new LinkedHashMap<>();
    }
    clientDetails.put("soroban", registeredInput.isSoroban() ? "true" : "false");
    ActivityCsv activityCsv =
        new ActivityCsv(
            "REGISTER_INPUT", registeredInput.getPoolId(), registeredInput, null, clientDetails);
    exportService.exportActivity(activityCsv);
  }

  private InputPool getPoolQueue(RegisteredInput registeredInput) throws NotifiableException {
    return getPoolQueue(registeredInput.getPoolId(), registeredInput.isLiquidity());
  }

  public InputPool getPoolQueue(String poolId, boolean liquidity) throws NotifiableException {
    Pool pool = getPool(poolId);
    if (liquidity) {
      // liquidity
      return pool.getLiquidityQueue();
    }
    // mustMix
    return pool.getMustMixQueue();
  }

  public void resetLastUserHash(Mix mix) {
    mix.getPool().getLiquidityQueue().resetLastUserHash();
    mix.getPool().getMustMixQueue().resetLastUserHash();
  }

  private void onClientDisconnect(String username) {
    Map<String, String> clientDetails = ImmutableMap.of("u", username);

    for (Pool pool : getPools()) {
      // remove queued liquidity
      Optional<RegisteredInput> liquidityRemoved =
          pool.getLiquidityQueue().removeByUsername(username);
      if (liquidityRemoved.isPresent()) {
        if (log.isDebugEnabled()) {
          log.debug("-INPUT_QUEUE_CLASSIC " + pool.getPoolId() + " " + liquidityRemoved.get());
        }

        // log activity
        ActivityCsv activityCsv =
            new ActivityCsv(
                "DISCONNECT", pool.getPoolId(), liquidityRemoved.get(), null, clientDetails);
        exportService.exportActivity(activityCsv);
      }

      // remove queued mustMix
      Optional<RegisteredInput> mustMixRemoved = pool.getMustMixQueue().removeByUsername(username);
      if (mustMixRemoved.isPresent()) {
        log.info("-INPUT_QUEUE_CLASSIC " + pool.getPoolId() + " " + mustMixRemoved.get());

        // log activity
        ActivityCsv activityCsv =
            new ActivityCsv(
                "DISCONNECT", pool.getPoolId(), mustMixRemoved.get(), null, clientDetails);
        exportService.exportActivity(activityCsv);
      }
    }
  }

  public int getNbInputs() {
    return pools.values().stream()
        .mapToInt(pool -> pool.getLiquidityQueue().getSize() + pool.getMustMixQueue().getSize())
        .sum();
  }

  public int getNbInputsBySoroban(boolean soroban) {
    return pools.values().stream()
        .mapToInt(
            pool ->
                pool.getLiquidityQueue().getSizeBySoroban(soroban)
                    + pool.getMustMixQueue().getSizeBySoroban(soroban))
        .sum();
  }
}
