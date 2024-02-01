package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.soroban.client.SorobanPayloadable;
import com.samourai.soroban.client.endpoint.controller.SorobanControllerTyped;
import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.soroban.protocol.payload.SorobanErrorMessage;
import com.samourai.wallet.bip47.rpc.Bip47Encrypter;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.protocol.SorobanAppWhirlpool;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.soroban.payload.registerInput.RegisterInputRequest;
import com.samourai.whirlpool.protocol.soroban.payload.registerInput.RegisterInputResponse;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.SorobanInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import java.lang.invoke.MethodHandles;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterInputPerPoolControllerSoroban extends SorobanControllerTyped {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;
  private RegisterInputService registerInputService;
  private String poolId;

  public RegisterInputPerPoolControllerSoroban(
      WhirlpoolServerContext serverContext,
      SorobanAppWhirlpool sorobanAppWhirlpool,
      PoolService poolService,
      RegisterInputService registerInputService,
      String poolId) {
    super(
        0,
        "REGISTER_INPUT[" + poolId + "]",
        serverContext.getRpcSession(),
        sorobanAppWhirlpool.getEndpointRegisterInput(
            serverContext.getCoordinatorWallet().getPaymentCode(), poolId));
    this.poolService = poolService;
    this.registerInputService = registerInputService;
    this.poolId = poolId;
  }

  @PreDestroy
  @Override
  public synchronized void stop() {
    super.stop();
  }

  @Override
  protected SorobanPayloadable computeReplyOnRequestNew(SorobanItemTyped request, String key)
      throws Exception {
    return computeReply(request);
  }

  @Override
  protected SorobanPayloadable computeReplyOnRequestExisting(SorobanItemTyped request, String key)
      throws Exception {
    return computeReply(request);
  }

  @Override
  protected SorobanPayloadable computeReplyOnRequestIgnored(SorobanItemTyped request, String key)
      throws Exception {
    return computeReply(request);
  }

  private SorobanPayloadable computeReply(SorobanItemTyped request) throws Exception {
    RegisterInputRequest registerInputRequest = request.read(RegisterInputRequest.class);
    try {
      // validate & register
      checkPoolId(registerInputRequest.poolId);
      return doRegisterInput(request, registerInputRequest);
    } catch (Exception e) {
      // reply error
      log.warn("+invalidInput: " + request.toString(), e);
      if (e instanceof IllegalInputException) {
        // specific error => errorCode message
        IllegalInputException ee = (IllegalInputException) e;
        return new SorobanErrorMessage(ee.getErrorCode(), ee.getMessage());
      } else {
        // unknown error => generic message
        String message =
            "Input rejected: " + NotifiableException.computeNotifiableException(e).getMessage();
        return new SorobanErrorMessage(WhirlpoolErrorCode.INPUT_REJECTED, message);
      }
    }
  }

  private SorobanPayloadable doRegisterInput(
      SorobanItemTyped request, RegisterInputRequest registerInputRequest) throws Exception {
    if (RegisterInputService.HEALTH_CHECK_UTXO.equals(registerInputRequest.utxoHash)) {
      return null; // ignore HEALTH_CHECK
    }

    PaymentCode paymentCode = request.getMetaSender();
    String username =
        paymentCode.toString(); // use Soroban sender (which is a temporary identity) as username

    // find from confirming
    Pool pool = poolService.getPool(registerInputRequest.poolId);
    Mix mix = pool.getCurrentMix();
    RegisteredInput registeredInput =
        mix.getConfirmingInputs()
            .find(registerInputRequest.utxoHash, registerInputRequest.utxoIndex, username)
            .orElse(null);

    if (registeredInput == null) {
      // not confirming

      // find from pool queue
      registeredInput =
          poolService
              .getPoolQueue(registerInputRequest.poolId, registerInputRequest.liquidity)
              .find(registerInputRequest.utxoHash, registerInputRequest.utxoIndex, username)
              .orElse(null);
      if (registeredInput == null) {
        // new input => add to registered inputs
        Bip47Encrypter encrypter = rpcSession.getRpcWallet().getBip47Encrypter();
        SorobanInput sorobanInput =
            new SorobanInput(paymentCode, request.getEndpointReply(encrypter));
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
        if (log.isDebugEnabled()) {
          log.debug("+sorobanInput: " + registeredInput.toString());
        }
      } else {
        // update existing input
        registeredInput.getSorobanInput().setSorobanLastSeen();
      }
      return null; // not confirming
    } else {
      // already confirming
      return new RegisterInputResponse(mix.getMixId(), mix.getPublicKey());
    }
  }

  // for tests
  public void _runOrchestrator() {
    super.runOrchestrator();
  }

  private void checkPoolId(String requestPoolId) throws Exception {
    if (!poolId.equals(requestPoolId)) {
      throw new NotifiableException(WhirlpoolErrorCode.INVALID_ARGUMENT, "Invalid poolId");
    }
  }
}
