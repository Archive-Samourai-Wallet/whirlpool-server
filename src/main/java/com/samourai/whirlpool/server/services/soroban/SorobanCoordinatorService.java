package com.samourai.whirlpool.server.services.soroban;

import com.samourai.soroban.client.SorobanPayload;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.whirlpool.protocol.SorobanProtocolWhirlpool;
import com.samourai.whirlpool.protocol.soroban.api.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.server.beans.SorobanInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.orchestrators.SorobanCoordinatorOrchestrator;
import com.samourai.whirlpool.server.orchestrators.SorobanUpStatusOrchestrator;
import com.samourai.whirlpool.server.services.MinerFeeService;
import com.samourai.whirlpool.server.services.PoolService;
import io.reactivex.Completable;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SorobanCoordinatorService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WhirlpoolServerContext serverContext;
  private SorobanProtocolWhirlpool sorobanProtocolWhirlpool;

  private SorobanUpStatusOrchestrator sorobanUpStatusOrchestrator;
  private SorobanCoordinatorOrchestrator coordinatorOrchestrator;

  @Autowired
  public SorobanCoordinatorService(
      PoolService poolService,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator,
      WhirlpoolServerConfig serverConfig,
      WhirlpoolServerContext serverContext,
      SorobanProtocolWhirlpool sorobanProtocolWhirlpool,
      MinerFeeService minerFeeService) {
    this.serverContext = serverContext;
    this.sorobanProtocolWhirlpool = sorobanProtocolWhirlpool;

    // start watching soroban statuses
    RpcSession rpcSession = serverContext.getRpcSession();
    sorobanUpStatusOrchestrator =
        new SorobanUpStatusOrchestrator(
            serverConfig, serverContext, rpcSession, sorobanProtocolWhirlpool);
    sorobanUpStatusOrchestrator.start(true);

    // start publishing pools
    coordinatorOrchestrator =
        new SorobanCoordinatorOrchestrator(
            serverConfig, serverContext, poolService, minerFeeService, whirlpoolApiCoordinator);
    coordinatorOrchestrator.start(true);
  }

  public Completable sendNotificationToClient(
      SorobanInput sorobanInput, SorobanPayload sorobanPayload) throws Exception {
    String requestId =
        sorobanProtocolWhirlpool.getRequestId(sorobanInput.getRequestNonceAndIncrement());
    return Completable.fromSingle(
        serverContext
            .getWhirlpoolPartnerApi(sorobanInput.getBip47Partner())
            .sendReplyEncrypted(sorobanPayload, requestId));
  }

  public Completable sendNotificationToClients(
      Collection<SorobanInput> sorobanInputs, SorobanPayload sorobanPayload) throws Exception {
    List<Completable> completables = new LinkedList<>();
    for (SorobanInput sorobanInput : sorobanInputs) {
      // TODO optimize with one single RPC call to multiple directories?
      completables.add(sendNotificationToClient(sorobanInput, sorobanPayload));
    }
    return Completable.merge(completables);
  }

  public void stop() {
    coordinatorOrchestrator.stop();
  }

  public SorobanCoordinatorOrchestrator _getCoordinatorOrchestrator() { // for tests
    return coordinatorOrchestrator;
  }
}
