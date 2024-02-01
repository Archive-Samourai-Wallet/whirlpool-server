package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.soroban.client.SorobanPayloadable;
import com.samourai.soroban.client.endpoint.controller.SorobanControllerTypedWithCachedReply;
import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.soroban.protocol.payload.AckResponse;
import com.samourai.soroban.protocol.payload.SorobanErrorMessage;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.protocol.SorobanAppWhirlpool;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.soroban.payload.mix.*;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.MixStatus;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import com.samourai.whirlpool.server.services.ConfirmInputService;
import com.samourai.whirlpool.server.services.RegisterOutputService;
import com.samourai.whirlpool.server.services.RevealOutputService;
import com.samourai.whirlpool.server.services.SigningService;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerMixControllerSoroban extends SorobanControllerTypedWithCachedReply {
  private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private ConfirmInputService confirmInputService;
  private RegisterOutputService registerOutputService;
  private SigningService signingService;
  private RevealOutputService revealOutputService;
  private Mix mix;

  public PerMixControllerSoroban(
      WhirlpoolServerContext serverContext,
      SorobanAppWhirlpool sorobanAppWhirlpool,
      ConfirmInputService confirmInputService,
      RegisterOutputService registerOutputService,
      SigningService signingService,
      RevealOutputService revealOutputService,
      Mix mix) {
    super(
        0,
        mix.getMixId(),
        serverContext.getRpcSession(),
        sorobanAppWhirlpool.getEndpointMix_ALL(
            mix.getMixId(), serverContext.getCoordinatorWallet().getPaymentCode()));
    log = Utils.prefixLogger(log, "MIX/" + mix.getMixId());
    this.confirmInputService = confirmInputService;
    this.registerOutputService = registerOutputService;
    this.signingService = signingService;
    this.revealOutputService = revealOutputService;
    this.mix = mix;
  }

  @Override
  protected Collection<SorobanItemTyped> fetch() throws Exception {
    MixStatus mixStatus = mix.getMixStatus();

    if ( // no SorobanInput mixing yet
    mix.getInputs().getListBySoroban(true).isEmpty()
        && (!MixStatus.CONFIRM_INPUT.equals(mixStatus)
            // no SorobanInput confirming yet
            || mix.getConfirmingInputs().getListBySoroban(true).isEmpty())) {
      // no mixing input from Soroban yet
      return new LinkedList<>();
    }

    // fetch from Soroban
    return super.fetch();
  }

  @Override
  protected void onRequestExisting(SorobanItemTyped request, String key) throws Exception {
    super.onRequestExisting(request, key);

    if (!request.isTyped(ConfirmInputRequest.class)) {
      // find mix input
      PaymentCode sender = request.getMetaSender();
      RegisteredInput registeredInput =
          mix.getInputs()
              .findBySorobanSender(sender)
              .orElseThrow(
                  () ->
                      new IllegalInputException(
                          WhirlpoolErrorCode.INPUT_REJECTED,
                          "Mix input not found for mixId="
                              + mix.getMixId()
                              + " and sender="
                              + sender.toString()));

      // update last seen
      registeredInput.getSorobanInput().setSorobanLastSeen();
    }
  }

  @Override
  protected SorobanPayloadable computeReplyOnRequestNewForCaching(
      SorobanItemTyped request, String key) {
    try {
      if (request.isTyped(ConfirmInputRequest.class)) {
        // input is not confirmed yet
        return confirmInput(request, request.read(ConfirmInputRequest.class));
      }

      // find mix input
      PaymentCode sender = request.getMetaSender();
      RegisteredInput registeredInput =
          mix.getInputs()
              .findBySorobanSender(sender)
              .orElseThrow(
                  () ->
                      new IllegalInputException(
                          WhirlpoolErrorCode.INPUT_REJECTED,
                          "Mix input not found for mixId="
                              + mix.getMixId()
                              + " and sender="
                              + sender.toString()));

      // update last seen
      registeredInput.getSorobanInput().setSorobanLastSeen();

      // reply
      if (request.isTyped(MixStatusRequest.class)) {
        return mixStatus(registeredInput);
      }
      if (request.isTyped(RegisterOutputRequest.class)) {
        return registerOutput(request.read(RegisterOutputRequest.class));
      }
      if (request.isTyped(SigningRequest.class)) {
        return signing(registeredInput, request.read(SigningRequest.class));
      }
      if (request.isTyped(RevealOutputRequest.class)) {
        return revealOutput(registeredInput, request.read(RevealOutputRequest.class));
      }
      throw new Exception("Unexpected request type: " + request.getType());
    } catch (Exception e) {
      log.error("error processing " + request.getType(), e);
      return computeReplyError(e);
    }
  }

  protected SorobanPayloadable mixStatus(RegisteredInput registeredInput) throws Exception {
    // send mixStatusResponse
    return mix.getMixStatusResponse(registeredInput.getSorobanInput());
  }

  protected ConfirmInputResponse confirmInput(SorobanItemTyped request, ConfirmInputRequest payload)
      throws Exception {
    PaymentCode sender = request.getMetaSender();
    RegisteredInput registeredInput =
        mix.removeConfirmingInputBySender(sender)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        WhirlpoolErrorCode.SERVER_ERROR,
                        "Confirming input not found: sender=" + sender.toString()));
    registeredInput.getSorobanInput().setSorobanLastSeen();

    try {
      // confirm input and send back signed bordereau, or enqueue back to pool
      byte[] blindedBordereau = WhirlpoolProtocol.decodeBytes(payload.blindedBordereau64);
      byte[] signedBordereau =
          confirmInputService.confirmInput(
              mix, registeredInput, blindedBordereau, payload.userHash);

      // reply confirmInputResponse with signedBordereau
      String signedBordereau64 = WhirlpoolProtocol.encodeBytes(signedBordereau);
      return new ConfirmInputResponse(signedBordereau64);
    } catch (QueueInputException e) {
      // confirmInput rejected => disconnect Soroban input
      throw new NotifiableException(WhirlpoolErrorCode.INPUT_REJECTED, e.getMessage());
    }
  }

  protected AckResponse registerOutput(RegisterOutputRequest payload) throws Exception {
    // signing
    byte[] unblindedSignedBordereau =
        WhirlpoolProtocol.decodeBytes(payload.unblindedSignedBordereau64);
    byte[] bordereau = WhirlpoolProtocol.decodeBytes(payload.bordereau64);
    registerOutputService.registerOutput(
        payload.inputsHash, unblindedSignedBordereau, payload.receiveAddress, bordereau);

    // reply ACK
    return new AckResponse();
  }

  protected AckResponse signing(RegisteredInput registeredInput, SigningRequest payload)
      throws Exception {
    // signing
    signingService.signing(payload.witnesses64, mix, registeredInput);

    // reply ACK
    return new AckResponse();
  }

  protected AckResponse revealOutput(RegisteredInput registeredInput, RevealOutputRequest payload)
      throws Exception {
    // reveal output
    revealOutputService.revealOutput(payload.receiveAddress, mix, registeredInput);

    // reply ACK
    return new AckResponse();
  }

  protected SorobanErrorMessage computeReplyError(Exception e) {
    int errorCode;
    String message;
    if (e instanceof IllegalInputException) {
      errorCode = ((IllegalInputException) e).getErrorCode();
      message = e.getMessage();
    } else if (e instanceof NotifiableException) {
      errorCode = ((NotifiableException) e).getErrorCode();
      message = e.getMessage();
    } else {
      errorCode = WhirlpoolErrorCode.SERVER_ERROR;
      message = NotifiableException.computeNotifiableException(e).getMessage();
    }
    return new SorobanErrorMessage(errorCode, message);
  }
}
