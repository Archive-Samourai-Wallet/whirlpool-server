package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.soroban.protocol.payload.AckResponse;
import com.samourai.wallet.sorobanClient.SorobanPayloadable;
import com.samourai.whirlpool.protocol.SorobanAppWhirlpool;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.soroban.payload.mix.RegisterOutputRequest;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.services.RegisterOutputService;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterOutputControllerSoroban extends AbstractPerMixControllerSoroban {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private RegisterOutputService registerOutputService;

  public RegisterOutputControllerSoroban(
      WhirlpoolServerContext serverContext,
      SorobanAppWhirlpool sorobanAppWhirlpool,
      RegisterOutputService registerOutputService,
      Mix mix) {
    super(
        serverContext,
        sorobanAppWhirlpool.getEndpointMixRegisterOutput(
            mix.computeInputsHash(), serverContext.getCoordinatorWallet().getPaymentCode()),
        mix,
        "REGISTER_OUTPUT");
    this.registerOutputService = registerOutputService;
  }

  // RegisterOutputRequest can be cached as we use .distinctByUniqueId()
  // to allow multiple attempts on address reuse

  @Override
  protected SorobanPayloadable doComputeReply(SorobanItemTyped request) throws Exception {
    // reply
    return registerOutput(request.read(RegisterOutputRequest.class));
  }

  protected AckResponse registerOutput(RegisterOutputRequest payload) throws Exception {
    // find mix
    if (!mix.computeInputsHash().equals(payload.inputsHash)) {
      log.warn("REGISTER_OUTPUT rejected: no current mix for inputsHash=" + payload.inputsHash);
      // reject with generic message because we may not be responsible of this error (ie: another
      // client disconnected during the mix)
      throw new MixException("Mix not found");
    }

    byte[] bordereau = WhirlpoolProtocol.decodeBytes(payload.bordereau64);
    if (!mix.hasBordereau(bordereau)) { // ignore duplicate requests
      if (log.isDebugEnabled()) {
        log.debug(
            "(<) MIX_REGISTER_OUTPUT_SOROBAN " + mix.getMixId() + " " + payload.receiveAddress);
      }
      byte[] unblindedSignedBordereau =
          WhirlpoolProtocol.decodeBytes(payload.unblindedSignedBordereau64);
      registerOutputService.registerOutput(
          mix, unblindedSignedBordereau, payload.receiveAddress, bordereau);
    }

    // reply ACK
    return new AckResponse();
  }
}
