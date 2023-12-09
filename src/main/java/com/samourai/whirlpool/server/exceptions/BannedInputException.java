package com.samourai.whirlpool.server.exceptions;

import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;

public class BannedInputException extends IllegalInputException {

  public BannedInputException(String message) {
    super(WhirlpoolErrorCode.INPUT_BANNED, message);
  }
}
