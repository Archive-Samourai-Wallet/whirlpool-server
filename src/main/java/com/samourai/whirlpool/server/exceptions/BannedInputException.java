package com.samourai.whirlpool.server.exceptions;

import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;

public class BannedInputException extends IllegalInputException {

  public BannedInputException(String message, String inputInfo) {
    super(WhirlpoolErrorCode.INPUT_BANNED, message, inputInfo);
  }
}
