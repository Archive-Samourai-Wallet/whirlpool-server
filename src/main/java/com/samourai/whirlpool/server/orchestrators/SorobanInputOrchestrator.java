package com.samourai.whirlpool.server.orchestrators;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.soroban.client.RpcWallet;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.whirlpool.protocol.soroban.RegisterInputSorobanMessage;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisterInputSoroban;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.services.soroban.SorobanCoordinatorApi;
import io.reactivex.Completable;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SorobanInputOrchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final int LOOP_DELAY = 15000; // < registerInputFrequency

  private PoolService poolService;
  private SorobanCoordinatorApi sorobanCoordinatorApi;
  private RegisterInputService registerInputService;
  private RpcSession rpcSession;
  private RpcWallet rpcWallet;

  private Map<String, RegisteredInput> registeredInputs; // by paymentCode
  private Map<String, Long> rejectedInputs; // rejection time by paymentCode

  public SorobanInputOrchestrator(
      PoolService poolService,
      SorobanCoordinatorApi sorobanCoordinatorApi,
      RegisterInputService registerInputService,
      RpcSession rpcSession,
      RpcWallet rpcWallet) {
    super(LOOP_DELAY, 0, null);
    this.poolService = poolService;
    this.sorobanCoordinatorApi = sorobanCoordinatorApi;
    this.registerInputService = registerInputService;
    this.rpcSession = rpcSession;
    this.rpcWallet = rpcWallet;
  }

  @Override
  protected void runOrchestrator() {
    int freshInputs = 0;
    int existingInputs = 0;
    int invalidInputs = 0;
    if (log.isDebugEnabled()) {
      log.debug("Checking soroban inputs for " + poolService.getPools().size() + " pools...");
    }
    for (Pool pool : poolService.getPools()) {
      String poolId = pool.getPoolId();
      try {
        Collection<RegisterInputSoroban> registerInputSorobans =
            rpcSession.withRpcClientEncrypted(
                rce -> sorobanCoordinatorApi.getListRegisterInputSorobanByPoolId(rce, poolId));
        for (RegisterInputSoroban registerInputSoroban : registerInputSorobans) {
          String pCode = registerInputSoroban.getSorobanPaymentCode().toString();
          try {
            // check consistency
            if (!poolId.equals(registerInputSoroban.getSorobanMessage().poolId)) {
              throw new Exception("poolId mismatch");
            }
            if (!this.rejectedInputs.containsKey(pCode)) {
              // validate & register
              RegisteredInput registeredInput = register(registerInputSoroban);
              if (registeredInput != null) {
                freshInputs++;
              } else {
                existingInputs++;
              }
            } else {
              invalidInputs++;
            }
          } catch (Exception e) {
            log.warn("+invalidInput: " + pCode, e);
            invalidInputs++;
            rejectInput(registerInputSoroban, e).subscribe();
          }
        }
      } catch (Exception e) {
        log.error("Failed to list Soroban inputs for poolId=" + poolId, e);
      }
    }

    // clean expired inputs
    int expiredInputs = cleanup();
    if (log.isDebugEnabled()) {
      log.debug(
          "[SOROBAN] "
              + freshInputs
              + " fresh, "
              + existingInputs
              + " existing, "
              + invalidInputs
              + " invalid, "
              + expiredInputs
              + " expired");
    }
  }

  private Completable rejectInput(RegisterInputSoroban registerInputSoroban, Exception e)
      throws Exception {
    // add to rejected inputs
    String pCode = registerInputSoroban.getSorobanPaymentCode().toString();
    this.rejectedInputs.put(pCode, System.currentTimeMillis());

    // send reject response
    String message =
        "Input rejected: " + NotifiableException.computeNotifiableException(e).getMessage();
    return rpcSession.withRpcClientEncrypted(
        rce -> sorobanCoordinatorApi.sendError(rce, registerInputSoroban, rpcWallet, message));
  }

  private int cleanup() {
    long registerInputFrequencyMs =
        sorobanCoordinatorApi.getWhirlpoolProtocolSoroban().getRegisterInputFrequencyMs();
    long minLastSeen = System.currentTimeMillis() - (registerInputFrequencyMs * 2);

    // cleanup expired registeredInputs
    Set<RegisteredInput> registeredInputsExpired =
        registeredInputs.values().stream()
            .filter(registeredInput -> registeredInput.getSorobanLastSeen() < minLastSeen)
            .collect(Collectors.toSet()); // required to avoid ConcurrentModificationException
    int nbExpired = registeredInputsExpired.size();
    registeredInputsExpired.forEach(
        registeredInput -> {
          // cleanup input
          registeredInputs.remove(registeredInput.getSorobanPaymentCode().toString());
          // unregister from pool queue
          try {
            poolService.unregisterInput(registeredInput);
          } catch (Exception e) {
            log.error("", e);
          }
        });

    // cleanup expired rejectedInputs
    Set<Map.Entry<String, Long>> rejectedInputsExpired =
        rejectedInputs.entrySet().stream()
            .filter(e -> e.getValue() < minLastSeen)
            .collect(Collectors.toSet()); // required to avoid ConcurrentModificationException
    nbExpired += rejectedInputsExpired.size();
    rejectedInputsExpired.forEach(rejectedInput -> rejectedInputs.remove(rejectedInput.getKey()));
    return nbExpired;
  }

  private RegisteredInput register(RegisterInputSoroban registerInputSoroban) throws Exception {
    if (RegisterInputService.HEALTH_CHECK_UTXO.equals(
        registerInputSoroban.getSorobanMessage().utxoHash)) {
      return null; // ignore HEALTH_CHECK
    }

    String pCode = registerInputSoroban.getSorobanPaymentCode().toString();
    RegisteredInput registeredInput = registeredInputs.get(pCode);
    if (registeredInput == null) {
      // unknown input => add to registered inputs
      RegisterInputSorobanMessage risb = registerInputSoroban.getSorobanMessage();
      registeredInput =
          registerInputService.registerInput(
              risb.poolId,
              null,
              risb.signature,
              risb.utxoHash,
              risb.utxoIndex,
              risb.liquidity,
              null, // we never know if user is using Tor with Soroban
              risb.blockHeight,
              registerInputSoroban.getSorobanPaymentCode(),
              registerInputSoroban.getInitialPayload(),
              null);
      registeredInputs.put(pCode, registeredInput);
      if (log.isDebugEnabled()) {
        log.debug("+sorobanInput: " + registeredInput.toString());
      }
      return registeredInput;
    } else {
      // already registered => update last seen
      if (log.isDebugEnabled()) {
        log.debug("+sorobanLastSeen: " + registeredInput.getOutPoint().toString());
      }
      registeredInput.setSorobanLastSeen();
    }
    return null;
  }

  @Override
  protected void resetOrchestrator() {
    super.resetOrchestrator();

    registeredInputs = new LinkedHashMap<>();
    rejectedInputs = new LinkedHashMap<>();
  }

  @Override
  public synchronized void stop() {
    super.stop();

    registeredInputs.clear();
    rejectedInputs.clear();
  }

  public void _runOrchestrator() { // for tests
    runOrchestrator();
  }
}
