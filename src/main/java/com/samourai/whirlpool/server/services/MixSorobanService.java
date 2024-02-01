package com.samourai.whirlpool.server.services;

import com.google.common.eventbus.Subscribe;
import com.samourai.whirlpool.client.wallet.WhirlpoolEventService;
import com.samourai.whirlpool.protocol.SorobanAppWhirlpool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.event.MixStartEvent;
import com.samourai.whirlpool.server.beans.event.MixStopEvent;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.controllers.soroban.PerMixControllerSoroban;
import com.samourai.whirlpool.server.controllers.soroban.RegisterInputPerPoolControllerSoroban;
import com.samourai.whirlpool.server.controllers.soroban.Tx0PerPoolControllerSoroban;
import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MixSorobanService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WhirlpoolServerContext serverContext;
  private SorobanAppWhirlpool sorobanAppWhirlpool;
  private ConfirmInputService confirmInputService;
  private RegisterOutputService registerOutputService;
  private SigningService signingService;
  private RevealOutputService revealOutputService;
  private Map<String, PerMixControllerSoroban> mixControllerByMixId;

  public MixSorobanService(
      WhirlpoolServerContext serverContext,
      SorobanAppWhirlpool sorobanAppWhirlpool,
      ConfirmInputService confirmInputService,
      RegisterOutputService registerOutputService,
      SigningService signingService,
      RevealOutputService revealOutputService,
      PoolService poolService,
      Tx0Service tx0Service,
      RegisterInputService registerInputService) {
    this.serverContext = serverContext;
    this.sorobanAppWhirlpool = sorobanAppWhirlpool;
    this.confirmInputService = confirmInputService;
    this.registerOutputService = registerOutputService;
    this.signingService = signingService;
    this.revealOutputService = revealOutputService;
    mixControllerByMixId = new LinkedHashMap<>();

    WhirlpoolEventService.getInstance().register(this);

    // TODO MixSorobanService is instanciated *AFTER* MixService has pushed initial events
    //  so we need this to manage initial mixs on startup
    for (Pool pool : poolService.getPools()) {
      onMixStart(new MixStartEvent(pool.getCurrentMix()));

      // start TX0 controller per pool
      Tx0PerPoolControllerSoroban tx0PerPoolControllerSoroban =
          new Tx0PerPoolControllerSoroban(
              serverContext, tx0Service, sorobanAppWhirlpool, pool.getPoolId());
      tx0PerPoolControllerSoroban.start(true);

      // start REGISTER_INPUT controller per pool
      RegisterInputPerPoolControllerSoroban registerInputPerPoolControllerSoroban =
          new RegisterInputPerPoolControllerSoroban(
              serverContext,
              sorobanAppWhirlpool,
              poolService,
              registerInputService,
              pool.getPoolId());
      registerInputPerPoolControllerSoroban.start(true);
    }
  }

  @Subscribe
  public void onMixStart(MixStartEvent event) {
    Mix mix = event.getMix();
    String mixId = mix.getMixId();
    if (mixControllerByMixId.containsKey(mixId)) {
      log.error("Mix already managed: " + mixId);
      mixControllerByMixId.get(mixId).stop();
      mixControllerByMixId.remove(mixId);
    }
    if (log.isDebugEnabled()) {
      log.debug("Managing mix: " + mixId);
    }
    PerMixControllerSoroban mixController =
        new PerMixControllerSoroban(
            serverContext,
            sorobanAppWhirlpool,
            confirmInputService,
            registerOutputService,
            signingService,
            revealOutputService,
            mix);
    mixControllerByMixId.put(mixId, mixController);
    mixController.start(true);
  }

  @Subscribe
  public void onMixEnd(MixStopEvent event) {
    String mixId = event.getMix().getMixId();
    if (!mixControllerByMixId.containsKey(mixId)) {
      log.error("Unmanaging mix not found: " + mixId);
      return;
    }
    if (log.isDebugEnabled()) {
      log.debug("Unmanaging mix: " + mixId);
    }
    mixControllerByMixId.get(mixId).stop();
    mixControllerByMixId.remove(mixId);
  }
}
