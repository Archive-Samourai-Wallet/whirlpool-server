package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.v0.WhirlpoolEndpointV0;
import com.samourai.whirlpool.protocol.v0.rest.PoolInfo;
import com.samourai.whirlpool.protocol.v0.rest.PoolsResponse;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.services.PoolService;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Deprecated // pools are now published on Soroban by SorobanPoolInfoOrchestrator
@RestController
public class PoolsController extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;

  @Autowired
  public PoolsController(PoolService poolService) {
    this.poolService = poolService;
  }

  @RequestMapping(value = WhirlpoolEndpointV0.REST_POOLS, method = RequestMethod.GET)
  public PoolsResponse pools() {
    Collection<PoolInfo> poolInfos = computePoolInfosV0();
    PoolsResponse poolsResponse = new PoolsResponse(poolInfos.toArray(new PoolInfo[] {}));
    if (log.isTraceEnabled()) {
      log.trace("(<) POOLS");
    }
    return poolsResponse;
  }

  protected Collection<PoolInfo> computePoolInfosV0() {
    return poolService.getPools().parallelStream()
        .map(
            pool -> {
              Mix currentMix = pool.getCurrentMix();
              int nbRegistered =
                  currentMix.getNbConfirmingInputs()
                      + pool.getMustMixQueue().getSize()
                      + pool.getLiquidityQueue().getSize();
              int nbConfirmed = currentMix.getNbInputs();
              return new PoolInfo(
                  pool.getPoolId(),
                  pool.getDenomination(),
                  pool.getPoolFee().getFeeValue(),
                  pool.computeMustMixBalanceMin(),
                  pool.computeMustMixBalanceCap(),
                  pool.computeMustMixBalanceMax(),
                  pool.getAnonymitySet(),
                  pool.getMinMustMix(),
                  pool.getTx0MaxOutputs(),
                  nbRegistered,
                  pool.getAnonymitySet(),
                  currentMix.getMixStatus().getMixStatusV0(),
                  currentMix.getElapsedTime(),
                  nbConfirmed);
            })
        .collect(Collectors.toList());
  }
}
