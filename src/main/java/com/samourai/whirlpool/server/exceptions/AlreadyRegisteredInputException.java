package com.samourai.whirlpool.server.exceptions;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;

public class AlreadyRegisteredInputException extends NotifiableException {

  public AlreadyRegisteredInputException(String message) {
    super(WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED, message);
  }
}
