package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.rest.PoolInfo;
import com.samourai.whirlpool.protocol.rest.PoolsResponse;
import com.samourai.whirlpool.server.services.PoolService;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
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

  @RequestMapping(value = WhirlpoolEndpoint.REST_POOLS, method = RequestMethod.GET)
  public PoolsResponse pools() {
    Collection<PoolInfo> poolInfos = poolService.computePoolInfos();
    PoolsResponse poolsResponse = new PoolsResponse(poolInfos.toArray(new PoolInfo[] {}));
    return poolsResponse;
  }
}
