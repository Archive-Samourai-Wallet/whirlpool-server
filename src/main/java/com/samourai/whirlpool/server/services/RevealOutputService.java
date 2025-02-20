package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.server.beans.Mix;
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

  public synchronized void revealOutput(
      String receiveAddress, Mix mix, RegisteredInput confirmedInput) throws Exception {
    if (log.isDebugEnabled()) {
      log.debug(
          "(<) MIX_REVEAL_OUTPUT_"
              + confirmedInput.getTypeStr()
              + " "
              + mix.getMixId()
              + " "
              + confirmedInput.toString());
    }

    // verify this username didn't already reveal his output
    if (mix.hasRevealedOutput(confirmedInput)) {
      log.warn("Rejecting already revealed input: " + confirmedInput);
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED, "Output already revealed", confirmedInput);
    }
    // verify this receiveAddress was not already revealed (someone could try to register 2 inputs
    // and reveal same receiveAddress to block mix)
    if (mix.hasRevealedReceiveAddress(receiveAddress)) {
      log.warn("Rejecting already revealed receiveAddress: " + receiveAddress);
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED,
          "ReceiveAddress already revealed",
          confirmedInput);
    }

    // verify an output was registered with this receiveAddress
    if (!mix.getReceiveAddresses().contains(receiveAddress)) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INVALID_ARGUMENT, "Invalid receiveAddress", confirmedInput);
    }

    mix.addRevealedOutput(confirmedInput, receiveAddress);

    mixService.onRevealOutput(mix);
  }
}
