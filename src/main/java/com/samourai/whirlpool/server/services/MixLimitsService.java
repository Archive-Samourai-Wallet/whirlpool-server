package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.soroban.WhirlpoolApiClient;
import com.samourai.whirlpool.protocol.soroban.payload.beans.MixStatus;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.utils.timeout.ITimeoutWatcherListener;
import com.samourai.whirlpool.server.utils.timeout.TimeoutWatcher;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MixLimitsService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WhirlpoolServerConfig serverConfig;
  private MixService mixService;

  private Map<String, TimeoutWatcher> limitsWatchers;

  @Autowired
  public MixLimitsService(WhirlpoolServerConfig serverConfig) {
    this.serverConfig = serverConfig;
    this.mixService = null;

    this.__reset();
  }

  // avoids circular reference
  public void setMixService(MixService mixService) {
    this.mixService = mixService;
  }

  private TimeoutWatcher getLimitsWatcher(Mix mix) {
    String mixId = mix.getMixId();
    return limitsWatchers.get(mixId);
  }

  public synchronized void manage(Mix mix) {
    TimeoutWatcher limitsWatcher = getLimitsWatcher(mix);
    if (limitsWatcher == null) {
      String mixId = mix.getMixId();
      limitsWatcher = computeLimitsWatcher(mix);
      this.limitsWatchers.put(mixId, limitsWatcher);
    }
  }

  public void unmanage(Mix mix) {
    String mixId = mix.getMixId();
    TimeoutWatcher limitsWatcher = getLimitsWatcher(mix);
    if (limitsWatcher != null) {
      limitsWatcher.stop();
      limitsWatchers.remove(mixId);
    }
  }

  public void onMixStatusChange(Mix mix) {
    // reset timeout for new mixStatus
    TimeoutWatcher limitsWatcher = getLimitsWatcher(mix);
    if (limitsWatcher != null) { // may be null for tests
      limitsWatcher.resetTimeout();
    }
  }

  private TimeoutWatcher computeLimitsWatcher(Mix mix) {
    ITimeoutWatcherListener listener =
        new ITimeoutWatcherListener() {
          @Override
          public Long computeTimeToWait(TimeoutWatcher timeoutWatcher) {
            long elapsedTime = timeoutWatcher.computeElapsedTime();
            int mixTimeoutExtend = serverConfig.getMixTimeoutExtend();
            int mixTimeoutExtendPerSurge = serverConfig.getMixTimeoutExtendPerSurge();
            int mixTimeoutExtendTotal =
                mixTimeoutExtend + (mix.getSurge() * mixTimeoutExtendPerSurge);
            if (log.isDebugEnabled()) {
              log.debug(
                  "["
                      + mix.getMixId()
                      + "] limitsWatcher.computeTimeToWait: mixStatus="
                      + mix.getMixStatus()
                      + ", surge="
                      + mix.getSurge()
                      + ", mixTimeoutExtendTotal="
                      + mixTimeoutExtendTotal);
            }

            Long timeToWait = null;
            switch (mix.getMixStatus()) {
              case CONFIRM_INPUT:
                // timeout before adding more liquidities - use soroban's register_input polling
                // frequency
                timeToWait = WhirlpoolApiClient.REGISTER_INPUT_POLLING_FREQUENCY_MS - elapsedTime;
                break;

              case REGISTER_OUTPUT:
                timeToWait =
                    MixStatus.REGISTER_OUTPUT.getTimeoutMs() + mixTimeoutExtendTotal - elapsedTime;
                break;

              case SIGNING:
                timeToWait = MixStatus.SIGNING.getTimeoutMs() + mixTimeoutExtendTotal - elapsedTime;
                break;

              case REVEAL_OUTPUT:
                timeToWait =
                    MixStatus.REVEAL_OUTPUT.getTimeoutMs() + mixTimeoutExtendTotal - elapsedTime;
                break;

              default:
                // no timer
                if (log.isDebugEnabled()) {
                  log.debug(
                      "["
                          + mix.getMixId()
                          + "] limitsWatcher.computeTimeToWait => no timer: mixStatus="
                          + mix.getMixStatus());
                }
                break;
            }
            return timeToWait;
          }

          @Override
          public void onTimeout(TimeoutWatcher timeoutWatcher) {
            if (log.isTraceEnabled()) {
              log.debug(
                  "[" + mix.getMixId() + "] limitsWatcher.onTimeout: " + " " + mix.getMixStatus());
            }
            mixService.disconnectSorobanInputsExpired(mix);
            switch (mix.getMixStatus()) {
              case CONFIRM_INPUT:
                // confirm more inputs
                mixService.onTimeoutConfirmInput(mix);
                break;

              case REGISTER_OUTPUT:
                // go REVEAL_OUTPUT or FAIL
                mixService.onTimeoutRegisterOutput(mix);
                break;

              case REVEAL_OUTPUT:
                // go FAIL
                mixService.onTimeoutRevealOutput(mix);
                break;

              case SIGNING:
                // go FAIL
                mixService.onTimeoutSigning(mix);
                break;

              default:
                if (log.isDebugEnabled()) {
                  log.debug(
                      "["
                          + mix.getMixId()
                          + "] limitsWatcher.onTimeout => ignored: mixStatus="
                          + mix.getMixStatus());
                }
            }
          }
        };

    TimeoutWatcher mixLimitsWatcher =
        new TimeoutWatcher(listener, "limitsWatcher-" + mix.getMixId());
    return mixLimitsWatcher;
  }

  public Long getLimitsWatcherTimeToWait(Mix mix) {
    TimeoutWatcher limitsWatcher = getLimitsWatcher(mix);
    if (limitsWatcher != null) {
      return limitsWatcher.computeTimeToWait();
    }
    return null;
  }

  public Long getLimitsWatcherElapsedTime(Mix mix) {
    TimeoutWatcher limitsWatcher = getLimitsWatcher(mix);
    if (limitsWatcher != null) {
      return limitsWatcher.computeElapsedTime();
    }
    return null;
  }

  public void __simulateElapsedTime(Mix mix, long elapsedTimeSeconds) {
    String mixId = mix.getMixId();
    log.info("__simulateElapsedTime for mixId=" + mixId);
    TimeoutWatcher limitsWatcher = getLimitsWatcher(mix);
    limitsWatcher.__simulateElapsedTime(elapsedTimeSeconds);
  }

  public void __reset() {
    if (limitsWatchers != null) {
      limitsWatchers.values().forEach(watcher -> watcher.stop());
    }

    this.limitsWatchers = new ConcurrentHashMap<>();
  }
}
