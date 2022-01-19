package com.samourai.whirlpool.server.beans;

public enum FailMode {
  DISABLED,
  REGISTER_INPUT,
  CONFIRM_INPUT,
  CONFIRM_INPUT_BLAME,
  REGISTER_OUTPUT;

  FailMode() {}
}
