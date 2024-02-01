package com.samourai.whirlpool.server.orchestrators;

import com.samourai.soroban.client.SorobanServerDex;
import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.whirlpool.protocol.soroban.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.protocol.soroban.payload.upStatus.UpStatusMessage;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SorobanUpStatusOrchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final AsyncUtil asyncUtil = AsyncUtil.getInstance();

  private static final int LOOP_DELAY = 180000; // every 3min
  private static final int PROPAGATION_DELAY = 10000; // 10sec

  private WhirlpoolServerConfig serverConfig;
  private WhirlpoolApiCoordinator whirlpoolApiCoordinator;

  public SorobanUpStatusOrchestrator(
      WhirlpoolServerConfig serverConfig, WhirlpoolApiCoordinator whirlpoolApiCoordinator) {
    super(LOOP_DELAY, 0, null);
    this.serverConfig = serverConfig;
    this.whirlpoolApiCoordinator = whirlpoolApiCoordinator;
  }

  private Collection<String> computeServerUrls(boolean onion) {
    NetworkParameters params = serverConfig.getNetworkParameters();
    SorobanServerDex sorobanServerDex = SorobanServerDex.get(params);
    return sorobanServerDex.getServerUrls(onion);
  }

  @Override
  protected void runOrchestrator() {
    doRun(false);
    doRun(true);
  }

  private void doRun(boolean onion) {
    long checkId = System.currentTimeMillis();

    // send one UpStatusMessage per soroban node
    Collection<String> serverUrls = computeServerUrls(onion);
    for (String serverUrl : serverUrls) {
      try {
        asyncUtil.blockingAwait(
            whirlpoolApiCoordinator
                .getRpcSession()
                .withSorobanClient(
                    sorobanClient -> whirlpoolApiCoordinator.upStatusSend(sorobanClient, checkId),
                    serverUrl));
      } catch (Exception e) {
        log.error("upStatusSend(" + serverUrl + ") failed: " + e.getMessage());
      }
    }

    // wait for propagation delay
    sleepOrchestrator(PROPAGATION_DELAY, true);

    // fetch each node
    Map<String, Collection<SorobanItemTyped>> resultsByNode =
        serverUrls
            .parallelStream()
            .collect(
                Collectors.toMap(
                    serverUrl -> serverUrl, serverUrl -> fetchSorobanNode(serverUrl, checkId)));

    // find max propagation
    int maxPropagations =
        resultsByNode.values().stream().mapToInt(list -> list.size()).max().getAsInt();

    // update UpStatus
    int nbUp = 0;
    for (Map.Entry<String, Collection<SorobanItemTyped>> e : resultsByNode.entrySet()) {
      String serverUrl = e.getKey();
      Collection<SorobanItemTyped> messages = e.getValue();
      String origins =
          messages.stream()
              .map(
                  i -> {
                    try {
                      return i.read(UpStatusMessage.class).origin;
                    } catch (Exception ee) {
                      log.error("", ee);
                      return "null";
                    }
                  })
              .collect(Collectors.joining(", "));

      String info =
          messages.size() > 0 ? messages.size() + " nodes found: " + origins : "No node found";
      if (messages.size() == maxPropagations) {
        // UP
        RpcSession.getUpStatusPool().setStatusUp(serverUrl, info);
        nbUp++;
      } else {
        // DOWN
        RpcSession.getUpStatusPool().setStatusDown(serverUrl, info);
      }
    }

    if (nbUp == 0) {
      log.error("*** ALL SOROBAN NODES SEEMS DOWN! ***, onion=" + onion);
    } else {
      log.info(
          nbUp
              + "/"
              + serverUrls.size()
              + " up, maxPropagations="
              + maxPropagations
              + ", onion="
              + onion);
    }
  }

  private Collection<SorobanItemTyped> fetchSorobanNode(String serverUrl, long checkId) {
    try {
      return asyncUtil.blockingGet(
          whirlpoolApiCoordinator
              .getRpcSession()
              .withSorobanClient(
                  sorobanClient -> whirlpoolApiCoordinator.upStatusFetch(sorobanClient, checkId),
                  serverUrl));
    } catch (Exception e) {
      log.error("upStatusFetch(" + serverUrl + ") failed: " + e.getMessage());
      return new LinkedList<>();
    }
  }
}
