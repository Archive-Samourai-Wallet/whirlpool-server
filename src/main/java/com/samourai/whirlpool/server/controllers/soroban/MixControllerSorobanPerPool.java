package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.soroban.client.*;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.soroban.*;
import com.samourai.whirlpool.protocol.soroban.api.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.services.ConfirmInputService;
import com.samourai.whirlpool.server.services.RegisterOutputService;
import com.samourai.whirlpool.server.services.RevealOutputService;
import com.samourai.whirlpool.server.services.SigningService;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MixControllerSorobanPerPool
    extends AbstractControllerSoroban<UntypedPayloadWithSender> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private ConfirmInputService confirmInputService;
  private RegisterOutputService registerOutputService;
  private SigningService signingService;
  private RevealOutputService revealOutputService;
  private Pool pool;
  private String currentMixId;

  public MixControllerSorobanPerPool(
      WhirlpoolServerContext serverContext,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator,
      ConfirmInputService confirmInputService,
      RegisterOutputService registerOutputService,
      SigningService signingService,
      RevealOutputService revealOutputService,
      Pool pool) {
    super(
        LOOP_DELAY_FAST, "[MIX/" + pool.getPoolId() + "]", serverContext, whirlpoolApiCoordinator);
    this.confirmInputService = confirmInputService;
    this.registerOutputService = registerOutputService;
    this.signingService = signingService;
    this.revealOutputService = revealOutputService;
    this.pool = pool;
  }

  @PreDestroy
  @Override
  public synchronized void stop() {
    super.stop();
  }

  @Override
  protected synchronized Collection<UntypedPayloadWithSender> fetch() throws Exception {
    // TODO currentMix may change while processing requests from previous mix?
    currentMixId = pool.getCurrentMix().getMixId();
    return asyncUtil
        .blockingGet(whirlpoolApiCoordinator.fetchMixRequests(currentMixId))
        .distinctBySender()
        .getList();
  }

  @Override
  protected String computeKey(UntypedPayloadWithSender message) {
    return message.computeUniqueId();
  }

  /*protected <T extends SorobanPayload> T forwardException(Callable<T> process, PayloadWithSender request) throws Exception {
    try {
      return process.call();
    } catch (Exception e) {
      sendReplyToRequest(request, new SorobanErrorMessage(WhirlpoolErrorCode.SERVER_ERROR, NotifiableException.computeNotifiableException(e).getMessage()));
    }
  }*/

  @Override
  protected void process(UntypedPayloadWithSender message, String key) {
    try {
      //      message.readOnWithSender(ConfirmInputRequest.class, request ->
      // forwardException(confirmInput(request));
      message.readOnWithSender(ConfirmInputRequest.class, request -> confirmInput(request));
      message.readOnWithSender(RegisterOutputRequest.class, request -> registerOutput(request));
      message.readOnWithSender(SigningRequest.class, request -> signing(request));
      message.readOnWithSender(RevealOutputRequest.class, request -> revealOutput(request));
    } catch (Exception e) {
      log.error("", e);
    }
  }

  protected void confirmInput(PayloadWithSender<ConfirmInputRequest> request) throws Exception {
    try {
      ConfirmInputRequest payload = request.getPayload();

      // confirm input and send back signed bordereau, or enqueue back to pool
      byte[] blindedBordereau = WhirlpoolProtocol.decodeBytes(payload.blindedBordereau64);
      byte[] signedBordereau =
          confirmInputService.confirmInput(
              currentMixId, blindedBordereau, payload.userHash, request.getSender());

      // reply confirmInputResponse with signedBordereau
      String signedBordereau64 = WhirlpoolProtocol.encodeBytes(signedBordereau);
      final ConfirmInputResponse response = new ConfirmInputResponse(signedBordereau64);
      sendReplyToRequest(request, response);
    } catch (Exception e) {
      log.error("confirmInput failed", e);
      sendError(request, e);
    }
  }

  protected void registerOutput(PayloadWithSender<RegisterOutputRequest> request) throws Exception {
    try {
      RegisterOutputRequest payload = request.getPayload();

      // signing
      byte[] unblindedSignedBordereau =
          WhirlpoolProtocol.decodeBytes(payload.unblindedSignedBordereau64);
      byte[] bordereau = WhirlpoolProtocol.decodeBytes(payload.bordereau64);
      registerOutputService.registerOutput(
          payload.inputsHash, unblindedSignedBordereau, payload.receiveAddress, bordereau);

      // reply ACK
      sendReplyToRequest(request, new AckResponse());
    } catch (Exception e) {
      log.error("registerOutput failed", e);
      sendError(request, e);
    }
  }

  protected void signing(PayloadWithSender<SigningRequest> request) throws Exception {
    try {
      SigningRequest payload = request.getPayload();

      // signing
      signingService.signing(currentMixId, payload.witnesses64, request.getSender());

      // reply ACK
      sendReplyToRequest(request, new AckResponse());
    } catch (Exception e) {
      log.error("signing failed", e);
      sendError(request, e);
    }
  }

  protected void revealOutput(PayloadWithSender<RevealOutputRequest> request) throws Exception {
    try {
      RevealOutputRequest payload = request.getPayload();

      // signing
      revealOutputService.revealOutput(currentMixId, payload.receiveAddress, request.getSender());

      // reply ACK
      sendReplyToRequest(request, new AckResponse());
    } catch (Exception e) {
      log.error("revealOutput failed", e);
      sendError(request, e);
    }
  }
}
