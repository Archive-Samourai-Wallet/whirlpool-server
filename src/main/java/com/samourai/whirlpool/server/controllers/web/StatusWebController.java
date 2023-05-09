package com.samourai.whirlpool.server.controllers.web;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.controllers.web.beans.WhirlpoolDashboardTemplateModel;
import com.samourai.whirlpool.server.services.MixLimitsService;
import com.samourai.whirlpool.server.services.MixService;
import com.samourai.whirlpool.server.services.PoolService;
import java.lang.invoke.MethodHandles;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class StatusWebController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT = "/status/status";

  private WhirlpoolServerConfig serverConfig;
  private PoolService poolService;
  private MixService mixService;
  private MixLimitsService mixLimitsService;

  @Autowired
  public StatusWebController(
      WhirlpoolServerConfig serverConfig,
      PoolService poolService,
      MixService mixService,
      MixLimitsService mixLimitsService) {
    this.serverConfig = serverConfig;
    this.poolService = poolService;
    this.mixService = mixService;
    this.mixLimitsService = mixLimitsService;
  }

  @RequestMapping(value = ENDPOINT, method = RequestMethod.GET)
  public String status(Model model) throws Exception {
    new WhirlpoolDashboardTemplateModel(serverConfig, "status").apply(model);

    List<Map<String, Object>> pools = new ArrayList<>();
    poolService
        .getPools()
        .forEach(
            pool -> {
              Mix mix = pool.getCurrentMix();
              Map<String, Object> poolAttributes = new HashMap<>();
              poolAttributes.put("poolId", pool.getPoolId());
              poolAttributes.put("denomination", pool.getDenomination());
              poolAttributes.put("feeValue", pool.getPoolFee().getFeeValue());
              poolAttributes.put("feeAccept", pool.getPoolFee().getFeeAccept());
              poolAttributes.put("mixStatus", mix.getMixStatus());
              poolAttributes.put("anonymitySet", pool.getAnonymitySet());
              poolAttributes.put("anonymitySetWithSurge", mix.getAnonymitySetWithSurge());
              poolAttributes.put("minMustMix", pool.getMinMustMix());
              poolAttributes.put("minLiquidity", pool.getMinLiquidity());
              poolAttributes.put("poolSurge", pool.getSurge());
              poolAttributes.put("mixSurge", mix.getSurge());
              poolAttributes.put("minerFee", pool.getMinerFee());
              pool.getMinerFee().getMinerFeeMin(); // used in template
              pool.getMinerFee().getMinerFeeCap(); // used in template
              pool.getMinerFee().getMinerFeeMax(); // used in template
              pool.getMinerFee().getMinerFeeMix(); // used in template
              poolAttributes.put("tx0MaxOutputs", pool.getTx0MaxOutputs());
              poolAttributes.put("minerFeeAccumulated", mix.computeMinerFeeAccumulated());
              poolAttributes.put("nbInputs", mix.getNbInputs());
              poolAttributes.put("nbInputsNonSurge", mix.getNbInputsNonSurge());
              poolAttributes.put("nbInputsSurge", mix.getNbInputsSurge());
              poolAttributes.put("nbInputsMustMix", mix.getNbInputsMustMix());
              poolAttributes.put("nbInputsLiquidities", mix.getNbInputsLiquidities());
              poolAttributes.put("elapsedTime", mix.getElapsedTime());

              Long currentStepElapsedTime =
                  toSeconds(this.mixLimitsService.getLimitsWatcherElapsedTime(mix));
              Long currentStepRemainingTime =
                  toSeconds(this.mixLimitsService.getLimitsWatcherTimeToWait(mix));
              Double currentStepProgress =
                  currentStepElapsedTime != null && currentStepRemainingTime != null
                      ? Math.ceil(
                          Double.valueOf(currentStepElapsedTime)
                              / (currentStepElapsedTime + currentStepRemainingTime)
                              * 100)
                      : null;
              if (MixStatus.CONFIRM_INPUT.equals(mix.getMixStatus())) {
                // ignore progressLabel for CONFIRM_INPUT (liquidities)
                currentStepProgress = null;
              }
              poolAttributes.put("currentStepProgress", currentStepProgress);

              String currentStepProgressLabel =
                  computeCurrentStepProgressLabel(
                      mix.getMixStatus(), currentStepElapsedTime, currentStepRemainingTime);
              poolAttributes.put("currentStepProgressLabel", currentStepProgressLabel);

              poolAttributes.put("mustMixQueued", mix.getPool().getMustMixQueue().getSize());
              poolAttributes.put("liquiditiesQueued", mix.getPool().getLiquidityQueue().getSize());
              poolAttributes.put("mustMixQueued", mix.getPool().getMustMixQueue().getSize());

              Map<MixStatus, Timestamp> timeStatus = mix.getTimeStatus();
              List<StatusStep> steps = new ArrayList<>();
              steps.add(computeStep(MixStatus.CONFIRM_INPUT, timeStatus));
              steps.add(computeStep(MixStatus.REGISTER_OUTPUT, timeStatus));
              if (timeStatus.containsKey(MixStatus.REVEAL_OUTPUT)) {
                steps.add(computeStep(MixStatus.REVEAL_OUTPUT, timeStatus));
                steps.add(computeStep(MixStatus.FAIL, timeStatus));
              } else {
                steps.add(computeStep(MixStatus.SIGNING, timeStatus));
                if (timeStatus.containsKey(MixStatus.FAIL)) {
                  steps.add(computeStep(MixStatus.FAIL, timeStatus));
                } else {
                  steps.add(computeStep(MixStatus.SUCCESS, timeStatus));
                }
              }
              poolAttributes.put("steps", steps);

              List<StatusEvent> events = new ArrayList<>();
              events.add(
                  new StatusEvent(
                      mix.getTimeStarted(),
                      MixStatus.CONFIRM_INPUT.toString(),
                      "Mix id: " + mix.getMixId()));
              timeStatus.forEach(
                  ((mixStatus, timestamp) ->
                      events.add(new StatusEvent(timestamp, mixStatus.toString(), null))));
              poolAttributes.put("events", events);
              pools.add(poolAttributes);
            });
    model.addAttribute("pools", pools);
    return "status";
  }

  private StatusStep computeStep(MixStatus mixStatus, Map<MixStatus, Timestamp> timeStatus) {
    boolean isActive =
        (!timeStatus.isEmpty()
            && new ArrayList<>(timeStatus.keySet()).indexOf(mixStatus) == (timeStatus.size() - 1));
    boolean isDone = !isActive && timeStatus.containsKey(mixStatus);
    return new StatusStep(isDone, isActive, mixStatus.toString(), null);
  }

  private String computeCurrentStepProgressLabel(
      MixStatus mixStatus, Long currentStepElapsedTime, Long currentStepRemainingTime) {
    String progressLabel = null;

    if (currentStepElapsedTime != null && currentStepRemainingTime != null) {
      progressLabel =
          currentStepElapsedTime + "s elapsed, " + currentStepRemainingTime + "s remaining ";
      switch (mixStatus) {
        case REGISTER_OUTPUT:
          progressLabel += "to register outputs";
          break;
        case SIGNING:
          progressLabel += "to sign";
          break;
        case REVEAL_OUTPUT:
          progressLabel += "to reveal outputs";
          break;
      }
    }
    return progressLabel;
  }

  private Long toSeconds(Long milliseconds) {
    if (milliseconds == null) {
      return null;
    }
    return milliseconds / 1000;
  }
}
