package com.samourai.whirlpool.server.exceptions;

import com.samourai.whirlpool.protocol.rest.PushTxErrorResponse;

public class ServerErrorCode {
  public static final int SERVER_ERROR = 500;
  public static final int VERSION_MISMATCH = 600;
  public static final int INVALID_ARGUMENT = 601;

  public static final int INPUT_REJECTED = 610;
  public static final int INPUT_ALREADY_REGISTERED = 611;
  public static final int INPUT_BANNED = 612;

  public static final int PUSHTX_ERROR = PushTxErrorResponse.ERROR_CODE; // 620
}
