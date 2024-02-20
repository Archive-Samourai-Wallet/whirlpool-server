package com.samourai.whirlpool.server.services;

import com.google.common.eventbus.Subscribe;
import com.samourai.whirlpool.client.wallet.WhirlpoolEventService;
import com.samourai.whirlpool.protocol.SorobanAppWhirlpool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.event.MixProgressEvent;
import com.samourai.whirlpool.server.beans.event.MixStartEvent;
import com.samourai.whirlpool.server.beans.event.MixStopEvent;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.controllers.soroban.*;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MixSorobanService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WhirlpoolServerContext serverContext;
  private SorobanAppWhirlpool sorobanAppWhirlpool;
  private MixService mixService;
  private ConfirmInputService confirmInputService;
  private RegisterOutputService registerOutputService;
  private SigningService signingService;
  private RevealOutputService revealOutputService;

  public MixSorobanService(
      WhirlpoolServerContext serverContext,
      SorobanAppWhirlpool sorobanAppWhirlpool,
      MixService mixService,
      ConfirmInputService confirmInputService,
      RegisterOutputService registerOutputService,
      SigningService signingService,
      RevealOutputService revealOutputService,
      PoolService poolService,
      Tx0Service tx0Service) {
    this.serverContext = serverContext;
    this.sorobanAppWhirlpool = sorobanAppWhirlpool;
    this.mixService = mixService;
    this.confirmInputService = confirmInputService;
    this.registerOutputService = registerOutputService;
    this.signingService = signingService;
    this.revealOutputService = revealOutputService;

    WhirlpoolEventService.getInstance().register(this);

    // TODO MixSorobanService is instanciated *AFTER* MixService has pushed initial events
    //  so we need this to manage initial mixs on startup
    for (Pool pool : poolService.getPools()) {
      log.info("Starting Soroban pool: " + pool.getPoolId());
      onMixStart(new MixStartEvent(pool.getCurrentMix()));

      // start TX0 controller per pool
      Tx0PerPoolControllerSoroban tx0PerPoolControllerSoroban =
          new Tx0PerPoolControllerSoroban(
              serverContext, tx0Service, sorobanAppWhirlpool, pool.getPoolId());
      tx0PerPoolControllerSoroban.start(true);
    }
  }

  @Subscribe
  public void onMixStart(MixStartEvent event) {
    Mix mix = event.getMix();
    if (mix.getSorobanControllerMixStatus() != null) {
      log.error("Mix already managed: " + mix.getMixId());
      return;
    }
    if (log.isDebugEnabled()) {
      log.debug("Managing mix: " + mix.getMixId());
    }

    // start mixStatus
    MixStatusControllerSoroban mixStatusController =
        new MixStatusControllerSoroban(serverContext, sorobanAppWhirlpool, mix);
    mixStatusController.start(true);
    mix.setSorobanControllerMixStatus(mixStatusController);

    // start mixStep
    AbstractPerMixControllerSoroban mixStepController = computeMixStepControllerSoroban(mix);
    if (mixStepController != null) {
      mixStepController.start(true);
      mix.setSorobanControllerMixStep(mixStepController);
    }
  }

  private void startMixStepControllerSoroban(Mix mix) throws Exception {
    if (mix.getSorobanControllerMixStep() != null) {
      mix.getSorobanControllerMixStep().stop();
    }

    AbstractPerMixControllerSoroban mixStepController = computeMixStepControllerSoroban(mix);
    if (mixStepController != null) {
      mixStepController.start(true);
      mix.setSorobanControllerMixStep(mixStepController);
    }
  }

  @Subscribe
  public void onMixProgress(MixProgressEvent event) throws Exception {
    Mix mix = event.getMix();
    startMixStepControllerSoroban(mix);
  }

  @Subscribe
  public void onMixEnd(MixStopEvent event) {
    Mix mix = event.getMix();
    if (mix.getSorobanControllerMixStatus() == null) {
      log.error("Unmanaging mix not found: " + mix.getMixId());
      return;
    }
    if (log.isDebugEnabled()) {
      log.debug("Unmanaging mix: " + mix.getMixId());
    }
    mix.getSorobanControllerMixStatus().stop();
    if (mix.getSorobanControllerMixStep() != null) {
      mix.getSorobanControllerMixStep().stop();
    }
  }

  private AbstractPerMixControllerSoroban computeMixStepControllerSoroban(Mix mix) {
    switch (mix.getMixStatus()) {
      case CONFIRM_INPUT:
        return new ConfirmInputControllerSoroban(
            serverContext, sorobanAppWhirlpool, confirmInputService, mix);
      case REGISTER_OUTPUT:
        return new RegisterOutputControllerSoroban(
            serverContext, sorobanAppWhirlpool, registerOutputService, mix);
      case SIGNING:
        return new SigningControllerSoroban(
            serverContext, sorobanAppWhirlpool, signingService, mix);
      case REVEAL_OUTPUT:
        return new RevealOutputControllerSoroban(
            serverContext, sorobanAppWhirlpool, revealOutputService, mix);
      default:
        return null;
    }
  }
}
