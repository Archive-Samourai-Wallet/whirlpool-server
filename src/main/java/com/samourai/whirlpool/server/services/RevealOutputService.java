package com.samourai.whirlpool.server.services;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.MixStatus;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RevealOutputService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MixService mixService;

  @Autowired
  public RevealOutputService(MixService mixService) {
    this.mixService = mixService;
  }

  public void revealOutput_webSocket(String mixId, String receiveAddress, String username)
      throws Exception {
    // find confirmed input
    Mix mix = mixService.getMix(mixId, MixStatus.REVEAL_OUTPUT);
    RegisteredInput confirmedInput =
        mix.getInputs()
            .findByUsername(username)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        WhirlpoolErrorCode.INPUT_REJECTED,
                        "Input not found for revealOutput username=" + username));

    // revealOutput
    revealOutput(receiveAddress, mix, confirmedInput);
  }

  public void revealOutput(String mixId, String receiveAddress, PaymentCode sender)
      throws Exception {
    // find confirmed input
    Mix mix = mixService.getMix(mixId, MixStatus.REVEAL_OUTPUT);
    RegisteredInput confirmedInput =
        mix.getInputs()
            .findBySorobanSender(sender)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        WhirlpoolErrorCode.INPUT_REJECTED,
                        "Input not found for revealOutput sender=" + sender.toString()));

    // revealOutput
    revealOutput(receiveAddress, mix, confirmedInput);
  }

  protected synchronized void revealOutput(
      String receiveAddress, Mix mix, RegisteredInput confirmedInput) throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) [" + mix.getMixId() + "] revealOutput: " + confirmedInput.toString());
    }

    // verify this username didn't already reveal his output
    if (mix.hasRevealedOutput(confirmedInput)) {
      log.warn("Rejecting already revealed input: " + confirmedInput);
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED, "Output already revealed");
    }
    // verify this receiveAddress was not already revealed (someone could try to register 2 inputs
    // and reveal same receiveAddress to block mix)
    if (mix.hasRevealedReceiveAddress(receiveAddress)) {
      log.warn("Rejecting already revealed receiveAddress: " + receiveAddress);
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED, "ReceiveAddress already revealed");
    }

    // verify an output was registered with this receiveAddress
    if (!mix.getReceiveAddresses().contains(receiveAddress)) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INVALID_ARGUMENT, "Invalid receiveAddress");
    }

    mix.addRevealedOutput(confirmedInput, receiveAddress);
    log.info("[" + mix.getLogId() + "] revealOutput success: " + confirmedInput.toString());

    mixService.onRevealOutput(mix);
  }
}
