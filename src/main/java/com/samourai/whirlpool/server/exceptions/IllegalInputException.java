package com.samourai.whirlpool.server.exceptions;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.server.beans.RegisteredInput;

public class IllegalInputException extends NotifiableException {
  private String inputInfo;

  public IllegalInputException(int errorCode, String message, String inputInfo) {
    super(errorCode, message);
    this.inputInfo = inputInfo;
  }

  public IllegalInputException(int errorCode, String message, RegisteredInput registeredInput) {
    this(errorCode, message, registeredInput.toString());
  }

  public String getInputInfo() {
    return inputInfo;
  }
}
