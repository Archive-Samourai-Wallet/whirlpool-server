package com.samourai.whirlpool.server.services;

import com.google.common.collect.ImmutableMap;
import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.protocol.rest.PoolInfo;
import com.samourai.whirlpool.protocol.rest.PoolInfoSoroban;
import com.samourai.whirlpool.protocol.websocket.messages.SubscribePoolResponse;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.ServerErrorCode;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class PoolService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WhirlpoolServerConfig whirlpoolServerConfig;
  private CryptoService cryptoService;
  private ExportService exportService;
  private MetricService metricService;
  private Map<String, Pool> pools;

  @Autowired
  public PoolService(
      WhirlpoolServerConfig whirlpoolServerConfig,
      CryptoService cryptoService,
      ExportService exportService,
      MetricService metricService,
      WSSessionService wsSessionService,
      TaskScheduler taskScheduler) {
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.cryptoService = cryptoService;
    this.exportService = exportService;
    this.metricService = metricService;
    __reset();

    // listen websocket onDisconnect
    wsSessionService.addOnDisconnectListener(username -> onClientDisconnect(username));

    if (MetricService.MOCK) {
      taskScheduler.scheduleAtFixedRate(
          () -> {
            metricService.mockPools(getPools());
          },
          4000);
    }
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
            minerFee);
    pools.put(poolId, pool);
    metricService.manage(pool);
    return pool;
  }

  public Collection<Pool> getPools() {
    return pools.values();
  }

  public Collection<PoolInfo> computePoolInfos() {
    return getPools()
        .parallelStream()
        .map(
            pool -> {
              Mix currentMix = pool.getCurrentMix();
              int nbRegistered =
                  currentMix.getNbConfirmingInputs()
                      + pool.getMustMixQueue().getSize()
                      + pool.getLiquidityQueue().getSize();
              int nbConfirmed = currentMix.getNbInputs();
              return new PoolInfo(
                  pool.getPoolId(),
                  pool.getDenomination(),
                  pool.getPoolFee().getFeeValue(),
                  pool.computeMustMixBalanceMin(),
                  pool.computeMustMixBalanceCap(),
                  pool.computeMustMixBalanceMax(),
                  pool.getAnonymitySet(),
                  pool.getMinMustMix(),
                  pool.getTx0MaxOutputs(),
                  nbRegistered,
                  pool.getAnonymitySet(),
                  currentMix.getMixStatus(),
                  currentMix.getElapsedTime(),
                  nbConfirmed);
            })
        .collect(Collectors.toList());
  }

  public Collection<PoolInfoSoroban> computePoolInfosSoroban(long feePerB) {
    return getPools()
        .parallelStream()
        .map(
            pool ->
                new PoolInfoSoroban(
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
      throw new IllegalInputException(ServerErrorCode.INVALID_ARGUMENT, "Pool not found");
    }
    return pool;
  }

  public SubscribePoolResponse computeSubscribePoolResponse(String poolId)
      throws IllegalInputException {
    Pool pool = getPool(poolId);
    SubscribePoolResponse poolStatusNotification =
        new SubscribePoolResponse(
            cryptoService.getNetworkParameters().getPaymentProtocolId(),
            pool.getDenomination(),
            pool.computeMustMixBalanceMin(),
            pool.computeMustMixBalanceCap(),
            pool.computeMustMixBalanceMax());
    return poolStatusNotification;
  }

  public RegisteredInput registerInput(
      String poolId,
      String username,
      boolean liquidity,
      TxOutPoint txOutPoint,
      Boolean tor,
      PaymentCode sorobanPaymentCodeOrNull,
      String sorobanInitialPayloadOrNull,
      String lastUserHash)
      throws NotifiableException {
    Pool pool = getPool(poolId);

    // verify balance
    long inputBalance = txOutPoint.getValue();
    if (!pool.checkInputBalance(inputBalance, liquidity)) {
      long balanceMin = pool.computePremixBalanceMin(liquidity);
      long balanceMax = pool.computePremixBalanceMax(liquidity);
      throw new IllegalInputException(
          ServerErrorCode.INPUT_REJECTED,
          "Invalid input balance (expected: "
              + balanceMin
              + "-"
              + balanceMax
              + ", actual:"
              + txOutPoint.getValue()
              + ")");
    }

    RegisteredInput registeredInput =
        new RegisteredInput(
            poolId,
            username,
            liquidity,
            txOutPoint,
            tor,
            sorobanPaymentCodeOrNull,
            sorobanInitialPayloadOrNull,
            lastUserHash);

    // verify confirmations
    if (!isUtxoConfirmed(txOutPoint, liquidity)) {
      throw new IllegalInputException(ServerErrorCode.INPUT_REJECTED, "Input is not confirmed");
    }
    queueToPool(pool, registeredInput);
    return registeredInput;
  }

  private void queueToPool(Pool pool, RegisteredInput registeredInput) throws NotifiableException {
    InputPool queue;
    if (registeredInput.isLiquidity()) {
      // liquidity
      queue = pool.getLiquidityQueue();
    } else {
      // mustMix
      queue = pool.getMustMixQueue();
    }

    if (log.isDebugEnabled()) {
      log.debug("[" + pool.getPoolId() + "] +queue: " + registeredInput.toString());
    }

    // queue input
    queue.register(registeredInput);
  }

  public void resetLastUserHash(Mix mix) {
    mix.getPool().getLiquidityQueue().resetLastUserHash();
    mix.getPool().getMustMixQueue().resetLastUserHash();
  }

  private boolean isUtxoConfirmed(TxOutPoint txOutPoint, boolean liquidity) {
    int inputConfirmations = txOutPoint.getConfirmations();
    if (liquidity) {
      // liquidity
      int minConfirmationsMix =
          whirlpoolServerConfig.getRegisterInput().getMinConfirmationsLiquidity();
      if (inputConfirmations < minConfirmationsMix) {
        log.info(
            "input not confirmed: liquidity needs at least "
                + minConfirmationsMix
                + " confirmations: "
                + txOutPoint.getHash());
        return false;
      }
    } else {
      // mustMix
      int minConfirmationsTx0 =
          whirlpoolServerConfig.getRegisterInput().getMinConfirmationsMustMix();
      if (inputConfirmations < minConfirmationsTx0) {
        log.info(
            "input not confirmed: mustMix needs at least "
                + minConfirmationsTx0
                + " confirmations: "
                + txOutPoint.getHash());
        return false;
      }
    }
    return true;
  }

  private void onClientDisconnect(String username) {
    Map<String, String> clientDetails = ImmutableMap.of("u", username);

    for (Pool pool : getPools()) {
      // remove queued liquidity
      Optional<RegisteredInput> liquidityRemoved =
          pool.getLiquidityQueue().removeByUsername(username);
      if (liquidityRemoved.isPresent()) {
        if (log.isDebugEnabled()) {
          log.debug("[" + pool.getPoolId() + "] " + username + " removed 1 liquidity from pool");
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
        log.info("[" + pool.getPoolId() + "] " + username + " removed 1 mustMix from pool");

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
