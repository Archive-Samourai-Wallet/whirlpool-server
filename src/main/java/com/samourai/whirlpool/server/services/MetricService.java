package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.beans.MixStatus;
import com.samourai.whirlpool.server.beans.export.MixCsv;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MetricService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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

  public void onMixResult(MixCsv mix, Collection<RegisteredInput> inputs) {
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
    for (RegisteredInput input : inputs) {
      List<String> tags = new LinkedList<>();
      tags.add("poolId");
      tags.add(mix.getPoolId());
      if (input.getTor() != null) {
        tags.add("tor");
        tags.add(BooleanUtils.toStringTrueFalse(input.getTor()));
      }
      Metrics.counter(COUNTER_MIX_INPUT_TOTAL, tags.toArray(new String[] {})).increment();
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
        GAUGE_POOL_QUEUE_MUSTMIX, tagsTor, pool, p -> p.getMustMixQueue().getSizeByTor(true));
    Metrics.gauge(
        GAUGE_POOL_QUEUE_MUSTMIX, tagsClearnet, pool, p -> p.getMustMixQueue().getSizeByTor(false));

    // queue-liquidity
    Metrics.gauge(
        GAUGE_POOL_QUEUE_LIQUIDITY, tagsTor, pool, p -> p.getLiquidityQueue().getSizeByTor(true));
    Metrics.gauge(
        GAUGE_POOL_QUEUE_LIQUIDITY,
        tagsClearnet,
        pool,
        p -> p.getLiquidityQueue().getSizeByTor(false));

    // mixing-mustMix
    Iterable<Tag> tags = Arrays.asList(Tag.of("poolId", pool.getPoolId()));
    Metrics.gauge(
        GAUGE_POOL_MIXING_MUSTMIX, tags, pool, p -> p.getCurrentMix().getNbInputsMustMix());

    // mixing-liquidity
    Metrics.gauge(
        GAUGE_POOL_MIXING_LIQUIDITY, tags, pool, p -> p.getCurrentMix().getNbInputsLiquidities());

    // start time
    Metrics.gauge(
        GAUGE_MIX_START_TIME_SECONDS,
        tags,
        pool,
        p -> p.getCurrentMix().getTimeStarted().getTime() / 1000);
  }
}
