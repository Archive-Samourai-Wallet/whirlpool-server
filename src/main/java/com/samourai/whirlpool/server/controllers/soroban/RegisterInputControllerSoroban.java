package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.soroban.client.PayloadWithSender;
import com.samourai.soroban.client.UntypedPayloadWithSender;
import com.samourai.soroban.client.dialog.SorobanErrorMessage;
import com.samourai.wallet.bip47.rpc.Bip47Partner;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.util.Util;
import com.samourai.whirlpool.protocol.SorobanProtocolWhirlpool;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.soroban.RegisterInputRequest;
import com.samourai.whirlpool.protocol.soroban.api.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.SorobanInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RegisterInputControllerSoroban
    extends AbstractControllerSoroban<UntypedPayloadWithSender> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;
  private RegisterInputService registerInputService;
  private long registerInputFrequencyMs;

  private Map<String, RegisteredInput>
      registeredInputs; // already-processed RegisteredInput by sender

  public RegisterInputControllerSoroban(
      WhirlpoolServerContext serverContext,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator,
      PoolService poolService,
      RegisterInputService registerInputService,
      SorobanProtocolWhirlpool sorobanProtocolWhirlpool) {
    super(LOOP_DELAY_SLOW, "REGISTER_INPUT", serverContext, whirlpoolApiCoordinator);
    this.poolService = poolService;
    this.registerInputService = registerInputService;
    this.registerInputFrequencyMs = sorobanProtocolWhirlpool.getRegisterInputFrequencyMs();
  }

  @PreDestroy
  @Override
  public synchronized void stop() {
    super.stop();
  }

  @Override
  protected void resetOrchestrator() {
    super.resetOrchestrator();
    registeredInputs = new LinkedHashMap<>();
  }

  @Override
  protected void runOrchestrator() {
    super.runOrchestrator();

    // clean expired inputs
    expireInputs();
  }

  @Override
  protected Collection<UntypedPayloadWithSender> fetch() throws Exception {
    return asyncUtil
        .blockingGet(whirlpoolApiCoordinator.fetchRegisterInputRequests())
        .distinctBySender()
        .getList();
  }

  @Override
  protected String computeKey(UntypedPayloadWithSender message) {
    // avoid reprocessing same request
    return message.computeUniqueId();
  }

  @Override
  protected void process(UntypedPayloadWithSender message, String key) {
    try {
      message.readOnWithSender(
          RegisterInputRequest.class, registerInputRequest -> processInput(registerInputRequest));
    } catch (Exception e) {
      log.error("", e);
    }
  }

  @Override
  protected synchronized void onExisting(UntypedPayloadWithSender message, String key)
      throws Exception {
    // update input without reprocessing request (no need to update requestId as same key was
    // already processed)
    String pCode = message.getSender().toString();
    RegisteredInput registeredInput = registeredInputs.get(pCode);
    String requestId = whirlpoolApiCoordinator.getRequestId(message.getPayload());
    registeredInput.getSorobanInput().setSorobanLastSeen();
    if (log.isDebugEnabled()) {
      log.debug("+existing: " + Util.maskString(pCode) + " -> " + requestId);
    }
  }

  protected void processInput(PayloadWithSender<RegisterInputRequest> request) throws Exception {
    String pCode = request.getSender().toString();
    try {
      // validate & register
      registerInput(request);
    } catch (Exception e) {
      log.warn("+invalidInput: " + pCode, e);

      SorobanErrorMessage sorobanErrorMessage;
      if (e instanceof IllegalInputException) {
        // specific error => errorCode message
        IllegalInputException ee = (IllegalInputException) e;
        sorobanErrorMessage = new SorobanErrorMessage(ee.getErrorCode(), ee.getMessage());
      } else {
        // unknown error => generic message
        String message =
            "Input rejected: " + NotifiableException.computeNotifiableException(e).getMessage();
        sorobanErrorMessage = new SorobanErrorMessage(WhirlpoolErrorCode.INPUT_REJECTED, message);
      }
      sendReplyToRequest(request, sorobanErrorMessage);
    }
  }

  private void registerInput(PayloadWithSender<RegisterInputRequest> request) throws Exception {
    RegisterInputRequest registerInputRequest = request.getPayload();
    if (RegisterInputService.HEALTH_CHECK_UTXO.equals(registerInputRequest.utxoHash)) {
      return; // ignore HEALTH_CHECK
    }

    PaymentCode paymentCode = request.getSender();
    String requestId = whirlpoolApiCoordinator.getRequestId(request.getPayload().toPayload());
    String pCode = paymentCode.toString();
    RegisteredInput registeredInput = registeredInputs.get(pCode);
    if (registeredInput == null) {
      // new input => add to registered inputs
      Bip47Partner bip47Partner = serverContext.getBip47Partner(paymentCode);
      SorobanInput sorobanInput = new SorobanInput(bip47Partner, requestId);
      registeredInput =
          registerInputService.registerInput(
              registerInputRequest.poolId,
              paymentCode
                  .toString(), // use Soroban sender (which is a temporary identity) as username
              registerInputRequest.signature,
              registerInputRequest.utxoHash,
              registerInputRequest.utxoIndex,
              registerInputRequest.liquidity,
              null, // we never know if user is using Tor with Soroban
              registerInputRequest.blockHeight,
              sorobanInput,
              null);
      registeredInputs.put(pCode, registeredInput);
      if (log.isDebugEnabled()) {
        log.debug("+sorobanInput: " + registeredInput.toString());
      }
    } else {
      // already registered => update last seen and requestId which may have changed
      if (log.isDebugEnabled()) {
        log.debug("+sorobanLastSeen: " + registeredInput.getOutPoint().toString());
      }
      registeredInput.getSorobanInput().setSorobanLastSeen();
      registeredInput.getSorobanInput().setRequestId(requestId);
    }
  }

  private void expireInputs() {
    long minLastSeen = System.currentTimeMillis() - (registerInputFrequencyMs * 2);

    // cleanup expired registeredInputs
    Set<RegisteredInput> registeredInputsExpired =
        registeredInputs.values().stream()
            .filter(
                registeredInput ->
                    registeredInput.getSorobanInput().getSorobanLastSeen() < minLastSeen)
            .collect(Collectors.toSet()); // required to avoid ConcurrentModificationException
    int nbExpired = registeredInputsExpired.size();
    if (nbExpired > 0) {
      if (log.isDebugEnabled()) {
        log.debug("Unregistering " + nbExpired + " inputs expired");
      }
    }
    registeredInputsExpired.forEach(
        registeredInput -> {
          // cleanup input
          registeredInputs.remove(
              registeredInput
                  .getSorobanInput()
                  .getBip47Partner()
                  .getPaymentCodePartner()
                  .toString());
          // unregister from pool queue
          try {
            poolService.unregisterInput(registeredInput);
          } catch (Exception e) {
            log.error("", e);
          }
        });
  }
}
