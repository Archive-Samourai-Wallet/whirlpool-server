package com.samourai.whirlpool.server.orchestrators;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.soroban.client.RpcWallet;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.soroban.RegisterInputSorobanMessage;
import com.samourai.whirlpool.server.beans.InputPool;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisterInputSoroban;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.services.soroban.SorobanCoordinatorApi;
import com.samourai.whirlpool.server.utils.Utils;
import io.reactivex.Single;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SorobanInputOrchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final int LOOP_DELAY = 15000;

  private PoolService poolService;
  private SorobanCoordinatorApi sorobanCoordinatorApi;
  private RegisterInputService registerInputService;
  private RpcSession rpcSession;
  private RpcWallet rpcWallet;

  private InputPool registeredInputs;
  private Set<String> invalidInputKeys;

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
    List<String> seenInputs = new LinkedList<>();
    for (Pool pool : poolService.getPools()) {
      String poolId = pool.getPoolId();
      try {
        Collection<RegisterInputSoroban> registerInputSorobans =
            rpcSession.withRpcClientEncrypted(
                rpcWallet.getEncrypter(),
                rce -> sorobanCoordinatorApi.getListRegisterInputSorobanByPoolId(rce, poolId));
        for (RegisterInputSoroban registerInputSoroban : registerInputSorobans) {
          String validationKey = computeValidationKey(registerInputSoroban);
          RegisterInputSorobanMessage risb = registerInputSoroban.getSorobanMessage();
          seenInputs.add(Utils.computeInputId(risb.utxoHash, risb.utxoIndex));
          try {
            // check consistency
            if (!poolId.equals(registerInputSoroban.getSorobanMessage().poolId)) {
              throw new Exception("poolId mismatch");
            }
            if (!invalidInputKeys.contains(validationKey)) {
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
            log.warn("+sorobanInvalid: " + validationKey, e);
            invalidInputKeys.add(validationKey);
            invalidInputs++;
            rejectInput(registerInputSoroban, e);
          }
        }
      } catch (Exception e) {
        log.error("Failed to list Soroban inputs for poolId=" + poolId, e);
      }
    }
    int disconnectedInputs = cleanup(seenInputs);
    if (log.isDebugEnabled()) {
      log.debug(
          "[SOROBAN] "
              + seenInputs.size()
              + " messages, "
              + freshInputs
              + " fresh, "
              + existingInputs
              + " existing, "
              + invalidInputs
              + " invalid, "
              + disconnectedInputs
              + " disconnected");
    }
  }

  private Single<String> rejectInput(RegisterInputSoroban registerInputSoroban, Exception e)
      throws Exception {
    String message =
        "Input rejected: " + NotifiableException.computeNotifiableException(e).getMessage();
    return rpcSession
        .withRpcClientEncrypted(
            rpcWallet.getEncrypter(),
            rce -> // reject input
            sorobanCoordinatorApi.sendError(rce, registerInputSoroban, rpcWallet, message))
        .doAfterSuccess(
            rejectPayload -> {
              // unregister input
              RegisterInputSorobanMessage risb = registerInputSoroban.getSorobanMessage();
              unregisterInput(
                  risb.poolId,
                  registerInputSoroban.getInitialPayload(),
                  risb.utxoHash,
                  risb.utxoIndex);
            });
  }

  public void unregisterInput(String poolId, String initialPayload, String utxoHash, long utxoIndex)
      throws Exception {
    // remove from Soroban
    rpcSession.withRpcClient(
        rpcClient ->
            sorobanCoordinatorApi.unregisterInput(rpcClient, poolId, initialPayload).subscribe());
    // remove from local inputs
    registeredInputs.removeByUtxo(utxoHash, utxoIndex);
  }

  private int cleanup(Collection<String> seenInputs) {
    // TODO cleanup invalidInputKeys?
    long minLastSeen =
        System.currentTimeMillis() - (WhirlpoolProtocol.getSorobanRegisterInputFrequencyMs() * 2);
    List<RegisteredInput> disconnectedInputs =
        registeredInputs._getInputs().stream()
            .filter(
                registeredInput ->
                    // utxo expired
                    registeredInput.getSorobanLastSeen() < minLastSeen
                        // utxo unregistered from Soroban
                        || !seenInputs.contains(
                            Utils.computeInputId(registeredInput.getOutPoint())))
            .collect(Collectors.toList());
    disconnectedInputs.stream().forEach(registeredInput -> onDisconnect(registeredInput));
    return disconnectedInputs.size();
  }

  private String computeValidationKey(RegisterInputSoroban registerInputSoroban) {
    RegisterInputSorobanMessage risb = registerInputSoroban.getSorobanMessage();
    // all properties necessary to input validation
    return risb.poolId + ":" + risb.utxoHash + ":" + risb.utxoIndex + ":" + risb.signature;
  }

  private RegisteredInput register(RegisterInputSoroban registerInputSoroban) throws Exception {
    if (RegisterInputService.HEALTH_CHECK_UTXO.equals(
        registerInputSoroban.getSorobanMessage().utxoHash)) {
      return null; // ignore HEALTH_CHECK
    }

    // register input once
    RegisterInputSorobanMessage risb = registerInputSoroban.getSorobanMessage();
    Optional<RegisteredInput> registeredInputOpt =
        registeredInputs.findByUtxo(risb.utxoHash, risb.utxoIndex);
    if (!registeredInputOpt.isPresent()) {
      // fresh input
      PaymentCode paymentCode = registerInputSoroban.getSorobanPaymentCode();
      RegisteredInput registeredInput =
          registerInputService.registerInput(
              risb.poolId,
              null,
              risb.signature,
              risb.utxoHash,
              risb.utxoIndex,
              risb.liquidity,
              false, // we never know if user is using Tor with Soroban
              risb.blockHeight,
              paymentCode,
              registerInputSoroban.getInitialPayload(),
              null);
      registeredInputs.register(registeredInput);
      if (log.isDebugEnabled()) {
        log.debug("+sorobanInput: " + registeredInput.toString());
      }
      return registeredInput;
    } else {
      RegisteredInput registeredInput = registeredInputOpt.get();
      // already registered => update last seen
      if (log.isDebugEnabled()) {
        log.debug("+sorobanLastSeen: " + registeredInput.getOutPoint().toString());
      }
      registeredInput.setSorobanLastSeen();
    }
    return null;
  }

  private void onDisconnect(RegisteredInput registeredInput) {
    TxOutPoint outPoint = registeredInput.getOutPoint();
    registeredInputs.removeByUtxo(outPoint.getHash(), outPoint.getIndex());
    try {
      poolService.unregisterInput(registeredInput);
    } catch (Exception e) {
      log.error("", e);
    }
  }

  @Override
  protected void resetOrchestrator() {
    super.resetOrchestrator();

    registeredInputs = new InputPool();
    invalidInputKeys = new LinkedHashSet<>();
  }

  @Override
  public synchronized void stop() {
    super.stop();

    registeredInputs.clear();
    invalidInputKeys = new LinkedHashSet<>();
  }

  public void _runOrchestrator() { // for tests
    runOrchestrator();
  }
}
