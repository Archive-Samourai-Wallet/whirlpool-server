package com.samourai.whirlpool.server.orchestrators;

import com.samourai.soroban.client.rpc.RpcClientEncrypted;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.whirlpool.protocol.soroban.RegisterInputSorobanMessage;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisterInputSoroban;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.services.soroban.SorobanCoordinatorApi;
import java.lang.invoke.MethodHandles;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SorobanRegisterInputOrchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final int LOOP_DELAY = 10000;
  private static final int START_DELAY = 2000;

  private PoolService poolService;
  private SorobanCoordinatorApi sorobanCoordinatorApi;
  private RegisterInputService registerInputService;
  private RpcClientEncrypted rpcClient;

  private Map<String, RegisteredInput> registeredInputsByKey;
  private Set<String> invalidInputKeys;

  public SorobanRegisterInputOrchestrator(
      PoolService poolService,
      SorobanCoordinatorApi sorobanCoordinatorApi,
      RegisterInputService registerInputService,
      RpcClientEncrypted rpcClient) {
    super(LOOP_DELAY, START_DELAY, null);
    this.poolService = poolService;
    this.sorobanCoordinatorApi = sorobanCoordinatorApi;
    this.registerInputService = registerInputService;
    this.rpcClient = rpcClient;
  }

  @Override
  protected void runOrchestrator() {
    for (Pool pool : poolService.getPools()) {
      String poolId = pool.getPoolId();
      int freshInputs = 0;
      int existingInputs = 0;
      int invalidInputs = 0;

      try {
        Collection<RegisterInputSoroban> registerInputSorobans =
            sorobanCoordinatorApi.getListRegisterInputSorobanByPoolId(rpcClient, poolId);
        if (log.isDebugEnabled()) {
          log.debug(poolId + " inputs: " + registerInputSorobans.size() + " messages...");
        }
        for (RegisterInputSoroban registerInputSoroban : registerInputSorobans) {
          String inputKey = computeInputKey(registerInputSoroban);
          try {
            // check consistency
            if (!poolId.equals(registerInputSoroban.getSorobanMessage().poolId)) {
              throw new Exception("poolId mismatch");
            }
            if (!invalidInputKeys.contains(inputKey)) {
              // validate & register
              RegisteredInput registeredInput = register(registerInputSoroban, inputKey);
              if (registeredInput != null) {
                freshInputs++;
              } else {
                existingInputs++;
              }
            } else {
              invalidInputs++;
            }
          } catch (Exception e) {
            log.warn("Invalid input: " + inputKey, e);
            invalidInputKeys.add(inputKey);
            invalidInputs++;
          }
        }
        if (log.isDebugEnabled()) {
          log.debug(
              poolId
                  + " inputs => "
                  + freshInputs
                  + " freshs, "
                  + invalidInputs
                  + " invalids, "
                  + existingInputs
                  + " existings");
        }
      } catch (Exception e) {
        log.error("Failed to list Soroban inputs for poolId=" + poolId, e);
      }
    }
  }

  private String computeInputKey(RegisterInputSoroban registerInputSoroban) {
    RegisterInputSorobanMessage risb = registerInputSoroban.getSorobanMessage();
    // all properties necessary to input validation
    return risb.poolId
        + ":"
        + risb.utxoHash
        + ":"
        + risb.utxoIndex
        + ":"
        + risb.signature
        + ":"
        + risb.liquidity;
  }

  private RegisteredInput register(RegisterInputSoroban registerInputSoroban, String inputKey)
      throws Exception {
    if (RegisterInputService.HEALTH_CHECK_UTXO.equals(
        registerInputSoroban.getSorobanMessage().utxoHash)) {
      return null; // ignore HEALTH_CHECK
    }

    // register input once
    RegisteredInput registeredInput = registeredInputsByKey.get(inputKey);
    if (registeredInput == null) {
      // fresh input
      PaymentCode paymentCode = registerInputSoroban.getSorobanPaymentCode();
      String username = paymentCode.toString();
      RegisterInputSorobanMessage risb = registerInputSoroban.getSorobanMessage();
      registeredInput =
          registerInputService.registerInput(
              risb.poolId,
              username,
              risb.signature,
              risb.utxoHash,
              risb.utxoIndex,
              risb.liquidity,
              false, // TODO
              risb.blockHeight,
              paymentCode,
              null);
      registeredInputsByKey.put(inputKey, registeredInput);
      return registeredInput;
    } else {
      // already registered
      if (log.isDebugEnabled()) {
        log.debug("Skipping Soroban input: " + inputKey + " (already registered)");
      }
      // update last seen
      registeredInput.setSorobanLastSeen();
    }
    return null;
  }

  @Override
  protected void resetOrchestrator() {
    super.resetOrchestrator();

    registeredInputsByKey = new LinkedHashMap<>();
    invalidInputKeys = new LinkedHashSet<>();
  }

  @Override
  public synchronized void stop() {
    super.stop();

    registeredInputsByKey = new LinkedHashMap<>();
    invalidInputKeys = new LinkedHashSet<>();
  }

  public void _runOrchestrator() { // for tests
    runOrchestrator();
  }
}
