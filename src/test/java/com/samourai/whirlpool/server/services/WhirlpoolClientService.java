package com.samourai.whirlpool.server.services;

import com.samourai.soroban.client.SorobanConfig;
import com.samourai.whirlpool.client.wallet.beans.IndexRange;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WhirlpoolClientService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private SorobanConfig sorobanConfig;

  @Autowired
  public WhirlpoolClientService(SorobanConfig sorobanConfig) {
    this.sorobanConfig = sorobanConfig;
  }

  public WhirlpoolClientConfig createWhirlpoolClientConfig() {
    try {
      return new WhirlpoolClientConfig(sorobanConfig, null, IndexRange.FULL);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
