package com.samourai.whirlpool.server.beans;

public enum FailReason {
  FAIL_REGISTER_OUTPUTS,
  FAIL_SIGNING,
  FAIL_BROADCAST,
  REJECTED_OUTPUT,
  DISCONNECT,
  SPENT
}
