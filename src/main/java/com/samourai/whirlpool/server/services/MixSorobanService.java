package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.soroban.api.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.controllers.soroban.MixControllerSorobanPerPool;
import com.samourai.whirlpool.server.controllers.soroban.RegisterInputControllerSoroban;
import com.samourai.whirlpool.server.controllers.soroban.Tx0ControllerSoroban;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MixSorobanService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public MixSorobanService(
      WhirlpoolServerContext serverContext,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator,
      ConfirmInputService confirmInputService,
      RegisterOutputService registerOutputService,
      SigningService signingService,
      RevealOutputService revealOutputService,
      PoolService poolService,
      Tx0ControllerSoroban tx0ControllerSoroban,
      RegisterInputControllerSoroban registerInputControllerSoroban) {

    // start soroban controllers
    tx0ControllerSoroban.start(true);
    registerInputControllerSoroban.start(true);

    // start soroban controllers per pool
    for (Pool pool : poolService.getPools()) {
      if (log.isDebugEnabled()) {
        log.debug("Starting MixControllerSorobanPerPool: " + pool.getPoolId());
      }
      new MixControllerSorobanPerPool(
              serverContext,
              whirlpoolApiCoordinator,
              confirmInputService,
              registerOutputService,
              signingService,
              revealOutputService,
              pool)
          .start(true);
    }
  }
}
