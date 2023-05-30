package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.beans.export.MixCsv;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MetricService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static boolean MOCK = false; // METRICS MOCK

  private static final String COUNTER_MIX_SUCCESS_TOTAL = "whirlpool_mix_success_total";
  private static final String COUNTER_MIX_FAIL_TOTAL = "whirlpool_mix_fail_total";
  private static final String COUNTER_MIX_INPUT_TOTAL = "whirlpool_mix_input_total";

  private static final String COUNTER_BLAME_TOTAL = "whirlpool_blame_total";
  private static final String COUNTER_BAN_TOTAL = "whirlpool_ban_total";

  private static final String GAUGE_MIX_START_TIME_SECONDS = "whirlpool_mix_start_time_seconds";

  private static final String SUMMARY_MIX_SUCCESS_MINER_FEE_AMOUNT =
      "whirlpool_mix_success_miner_fee_amount";
  private static final String SUMMARY_MIX_SUCCESS_MINER_FEE_PRICE =
      "whirlpool_mix_success_miner_fee_price";
  private static final String SUMMARY_MIX_SUCCESS_ANONYMITY_SET =
      "whirlpool_mix_success_anonymity_set";
  private static final String TIMER_MIX_SUCCESS_DURATION = "whirlpool_mix_success_duration";
  private static final String SUMMARY_MIX_SUCCESS_VOLUME = "whirlpool_mix_success_volume";

  private static final String GAUGE_POOL_QUEUE_MUSTMIX = "whirlpool_pool_queue_mustmix";
  private static final String GAUGE_POOL_QUEUE_LIQUIDITY = "whirlpool_pool_queue_liquidity";

  private static final String GAUGE_POOL_MIXING_MUSTMIX = "whirlpool_pool_mixing_mustmix";
  private static final String GAUGE_POOL_MIXING_LIQUIDITY = "whirlpool_pool_mixing_liquidity";

  public MetricService() {}

  public void onMixResult(MixCsv mix, Collection<ConfirmedInput> inputs) {
    if (MixStatus.SUCCESS.equals(mix.getMixStatus())) {
      // mix success
      Metrics.counter(COUNTER_MIX_SUCCESS_TOTAL, "poolId", mix.getPoolId()).increment();

      Metrics.summary(SUMMARY_MIX_SUCCESS_MINER_FEE_AMOUNT, "poolId", mix.getPoolId())
          .record(mix.getFeesAmount());
      Metrics.summary(SUMMARY_MIX_SUCCESS_MINER_FEE_PRICE, "poolId", mix.getPoolId())
          .record(mix.getFeesPrice());
      Metrics.summary(SUMMARY_MIX_SUCCESS_ANONYMITY_SET, "poolId", mix.getPoolId())
          .record(mix.getAnonymitySet());
      Metrics.timer(TIMER_MIX_SUCCESS_DURATION, "poolId", mix.getPoolId())
          .record(Duration.of(mix.getMixDuration(), ChronoUnit.SECONDS));
      Metrics.summary(SUMMARY_MIX_SUCCESS_VOLUME, "poolId", mix.getPoolId())
          .record(mix.getAmountOut());
    } else {
      // mix fail
      Metrics.counter(COUNTER_MIX_FAIL_TOTAL, "poolId", mix.getPoolId()).increment();
    }

    // inputs
    for (ConfirmedInput confirmedInput : inputs) {
      RegisteredInput input = confirmedInput.getRegisteredInput();
      Metrics.counter(
              COUNTER_MIX_INPUT_TOTAL,
              "poolId",
              mix.getPoolId(),
              "tor",
              Boolean.toString(input.isTor()))
          .increment();
    }
  }

  public void onBlame(RegisteredInput input) {
    Metrics.counter(COUNTER_BLAME_TOTAL, "poolId", input.getPoolId()).increment();
  }

  public void onBan(RegisteredInput input) {
    Metrics.counter(COUNTER_BAN_TOTAL, "poolId", input.getPoolId()).increment();
  }

  public void manage(Pool pool) {
    // queue-mustMix
    Iterable<Tag> tagsTor =
        Arrays.asList(Tag.of("poolId", pool.getPoolId()), Tag.of("tor", Boolean.toString(true)));
    Iterable<Tag> tagsClearnet =
        Arrays.asList(Tag.of("poolId", pool.getPoolId()), Tag.of("tor", Boolean.toString(false)));
    Metrics.gauge(
        GAUGE_POOL_QUEUE_MUSTMIX, tagsTor, pool, p -> mock(p.getMustMixQueue().getSizeByTor(true)));
    Metrics.gauge(
        GAUGE_POOL_QUEUE_MUSTMIX,
        tagsClearnet,
        pool,
        p -> mock(p.getMustMixQueue().getSizeByTor(false)));

    // queue-liquidity
    Metrics.gauge(
        GAUGE_POOL_QUEUE_LIQUIDITY,
        tagsTor,
        pool,
        p -> mock(p.getLiquidityQueue().getSizeByTor(true)));
    Metrics.gauge(
        GAUGE_POOL_QUEUE_LIQUIDITY,
        tagsClearnet,
        pool,
        p -> mock(p.getLiquidityQueue().getSizeByTor(false)));

    // mixing-mustMix
    Iterable<Tag> tags = Arrays.asList(Tag.of("poolId", pool.getPoolId()));
    Metrics.gauge(
        GAUGE_POOL_MIXING_MUSTMIX, tags, pool, p -> mock(p.getCurrentMix().getNbInputsMustMix()));

    // mixing-liquidity
    Metrics.gauge(
        GAUGE_POOL_MIXING_LIQUIDITY,
        tags,
        pool,
        p -> mock(p.getCurrentMix().getNbInputsLiquidities()));

    // start time
    Metrics.gauge(
        GAUGE_MIX_START_TIME_SECONDS,
        tags,
        pool,
        p -> p.getCurrentMix().getTimeStarted().getTime() / 1000);
  }

  private long mock(long nb) {
    if (!MOCK) {
      return nb;
    }
    return nb + RandomUtils.nextInt(0, 30);
  }

  public void mockPools(Collection<Pool> pools) {
    if (!MOCK) {
      return;
    }
    for (Pool pool : pools) {
      TxOutPoint outPoint = new TxOutPoint("hash", RandomUtils.nextInt(0, 999999), 1, 1, null, "");
      RegisteredInput input =
          new RegisteredInput(
              pool.getPoolId(),
              "username" + RandomUtils.nextInt(0, 999999),
              false,
              outPoint,
              "127.0.0.1",
              "");
      TxOutPoint outPoint2 =
          new TxOutPoint("hash2", RandomUtils.nextInt(0, 999999), 1, 1, null, "");
      RegisteredInput input2 =
          new RegisteredInput(
              pool.getPoolId(),
              "username" + RandomUtils.nextInt(0, 999999),
              true,
              outPoint2,
              "1.2.3.4",
              "");

      Mix mix = pool.getCurrentMix();
      if (mix != null) {
        if (RandomUtils.nextBoolean()) {
          try {
            mix.registerConfirmingInput(input);
          } catch (Exception e) {
            log.error("", e);
          }
          try {
            mix.registerInput(new ConfirmedInput(input, "userHash"));
          } catch (Exception e) {
            log.error("", e);
          }
        }
        if (RandomUtils.nextBoolean()) {
          try {
            mix.registerConfirmingInput(input2);
          } catch (Exception e) {
            log.error("", e);
          }
          try {
            mix.registerInput(new ConfirmedInput(input2, "userHash2"));
          } catch (Exception e) {
            log.error("", e);
          }
        }
        if (RandomUtils.nextBoolean()) {
          onBlame(input);
          if (RandomUtils.nextBoolean()) {
            onBan(input);
          }
        }

        if (RandomUtils.nextBoolean()) {
          if (RandomUtils.nextBoolean()) {
            mix.setMixStatusAndTime(MixStatus.SUCCESS);
          } else {
            mix.setMixStatusAndTime(MixStatus.FAIL);
            if (RandomUtils.nextBoolean()) {
              mix.setFailReason(FailReason.DISCONNECT);
            } else if (RandomUtils.nextBoolean()) {
              mix.setFailReason(FailReason.FAIL_REGISTER_OUTPUTS);
            } else {
              mix.setFailReason(FailReason.FAIL_SIGNING);
            }
          }
          MixTO mixTO = mix.computeMixTO();
          mixTO.setFrom(mix, RandomUtils.nextLong(100, 10000), RandomUtils.nextLong(1, 1000));
          MixCsv mixCsv = new MixCsv(mixTO);
          onMixResult(mixCsv, mix.getInputs());
        }
      }
    }
  }
}
