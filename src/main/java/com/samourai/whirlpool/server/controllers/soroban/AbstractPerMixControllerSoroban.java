package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.soroban.client.endpoint.controller.SorobanControllerTypedWithCachedReply;
import com.samourai.soroban.client.endpoint.meta.typed.SorobanEndpointTyped;
import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.sorobanClient.SorobanPayloadable;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.soroban.payload.mix.ConfirmInputRequest;
import com.samourai.whirlpool.protocol.soroban.payload.mix.RegisterOutputRequest;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.MixStatus;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractPerMixControllerSoroban
    extends SorobanControllerTypedWithCachedReply {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected Mix mix;
  protected String controllerName;

  public AbstractPerMixControllerSoroban(
      WhirlpoolServerContext serverContext,
      SorobanEndpointTyped sorobanEndpoint,
      Mix mix,
      String controllerName) {
    super(
        0,
        "sorobanController=" + controllerName + ",mixId=" + mix.getMixId(),
        serverContext.getRpcSession(),
        sorobanEndpoint);
    this.mix = mix;
    this.controllerName = controllerName;
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
  protected final void onRequestExisting(SorobanItemTyped request, String key) throws Exception {
    super.onRequestExisting(request, key);

    if (log.isDebugEnabled()) {
      log.debug(
          "(<) MIX_INPUT_SOROBAN_CONTROLLER "
              + controllerName
              + " "
              + request.getType()
              + " mixId="
              + mix.getMixId()
              + " sender="
              + request.getMetaSender());
    }

    if (!request.isTyped(ConfirmInputRequest.class)
        && !request.isTyped(RegisterOutputRequest.class)) {
      // update last seen
      setMixInputLastSeen(request.getMetaSender());
    }
  }

  protected RegisteredInput setMixInputLastSeen(PaymentCode sender) throws Exception {
    // find mix input
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
    return registeredInput;
  }

  @Override
  protected final SorobanPayloadable computeReplyOnRequestNewForCaching(
      SorobanItemTyped request, String key) {
    if (log.isDebugEnabled()) {
      log.debug(
          "(<) MIX_INPUT_SOROBAN_CONTROLLER "
              + controllerName
              + " mixId="
              + mix.getMixId()
              + "sender="
              + request.getMetaSender());
    }
    // input is not confirmed yet
    try {
      return doComputeReplyOnRequestNewForCaching(request);
    } catch (Exception e) {
      log.error("error processing " + request.getType(), e);
      return Utils.computeSorobanErrorMessage(e);
    }
  }

  protected abstract SorobanPayloadable doComputeReplyOnRequestNewForCaching(
      SorobanItemTyped request) throws Exception;

  @Override
  protected void sendReply(SorobanItemTyped request, SorobanPayloadable response) throws Exception {
    super.sendReply(request, response);

    if (log.isDebugEnabled()) {
      log.debug(
          "(>) MIX_INPUT_SOROBAN_CONTROLLER "
              + controllerName
              + " mixId="
              + mix.getMixId()
              + " "
              + response.getClass().getName()
              + " sender="
              + request.getMetaSender());
    }
  }
}
