package com.samourai.whirlpool.server.exceptions;

import com.samourai.javaserver.exceptions.NotifiableException;

public class IllegalInputException extends NotifiableException {

  public IllegalInputException(int errorCode, String message) {
    super(errorCode, message);
  }
}
