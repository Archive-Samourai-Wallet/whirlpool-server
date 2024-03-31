package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.soroban.client.endpoint.controller.SorobanControllerTyped;
import com.samourai.soroban.client.endpoint.meta.typed.SorobanEndpointTyped;
import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.sorobanClient.SorobanPayloadable;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
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

public abstract class AbstractPerMixControllerSoroban extends SorobanControllerTyped {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected Mix mix;

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
      if (log.isTraceEnabled()) {
        log.trace("MIX_INPUT_NONE_SOROBAN " + mix.getMixId() + " " + mix.getLogStatus());
      }
      return new LinkedList<>();
    }

    // fetch from Soroban
    return super.fetch();
  }

  protected RegisteredInput setMixInputLastSeen(PaymentCode sender) throws Exception {
    // find mix input
    RegisteredInput registeredInput =
        mix.getInputs()
            .findBySorobanSender(sender)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        WhirlpoolErrorCode.MIX_OVER,
                        "Mix input not found",
                        "sender=" + sender.toString()));

    // update last seen
    registeredInput.getSorobanInput().setSorobanLastSeen();
    return registeredInput;
  }

  @Override
  protected final SorobanPayloadable computeReply(SorobanItemTyped request) {
    try {
      return doComputeReply(request);
    } catch (Exception e) {
      return Utils.computeSorobanErrorMessage(e);
    }
  }

  protected abstract SorobanPayloadable doComputeReply(SorobanItemTyped request) throws Exception;
}
