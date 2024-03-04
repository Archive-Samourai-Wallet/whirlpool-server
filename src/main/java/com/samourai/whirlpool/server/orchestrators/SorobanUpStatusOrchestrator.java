package com.samourai.whirlpool.server.orchestrators;

import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.sorobanClient.SorobanServerDex;
import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.wallet.util.Pair;
import com.samourai.wallet.util.urlStatus.UpStatusPool;
import com.samourai.whirlpool.protocol.soroban.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.protocol.soroban.payload.upStatus.UpStatusMessage;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.monitoring.MonitoringService;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class SorobanUpStatusOrchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final AsyncUtil asyncUtil = AsyncUtil.getInstance();

  private static final int LOOP_DELAY = 60000; // every 1min
  private static final int PROPAGATION_DELAY = 10000; // 10sec
  private static final int MIN_PROPAGATION = 2; // require at least 2 nodes synchronized

  private WhirlpoolServerConfig serverConfig;
  private WhirlpoolApiCoordinator whirlpoolApiCoordinator;
  private MonitoringService monitoringService;

  private Map<Boolean, Pair<Integer, Integer>> status; // pair(nbUp,nbDown) per onion
  private boolean statusAllDown;
  private String statusDegradedMode;
  private int statusNbUnsynchronized;

  public SorobanUpStatusOrchestrator(
      WhirlpoolServerConfig serverConfig,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator,
      MonitoringService monitoringService) {
    super(LOOP_DELAY, 0, null);
    this.serverConfig = serverConfig;
    this.whirlpoolApiCoordinator = whirlpoolApiCoordinator;
    this.monitoringService = monitoringService;

    this.status = new LinkedHashMap<>();
    this.status.put(true, Pair.of(0, 0));
    this.status.put(false, Pair.of(0, 0));
  }

  private Collection<String> computeSorobanUrls(boolean onion) {
    NetworkParameters params = serverConfig.getNetworkParameters();
    SorobanServerDex sorobanServerDex = SorobanServerDex.get(params);
    return sorobanServerDex.getSorobanUrls(onion);
  }

  @Override
  protected void runOrchestrator() {
    MDC.put("mdc", "orchestrator=SorobanUpStatusOrchestrator");
    doRun(false);
    doRun(true);
    MDC.clear();
    this.statusAllDown = false;
    this.statusDegradedMode = null;
    this.statusNbUnsynchronized = 0;
  }

  private void doRun(boolean onion) {
    UpStatusPool upStatusPool = RpcSession.getUpStatusPool();
    long checkId = System.currentTimeMillis();

    // send one UpStatusMessage per soroban node
    Collection<String> sorobanUrls = computeSorobanUrls(onion);
    for (String sorobanUrl : sorobanUrls) {
      try {
        asyncUtil.blockingAwait(
            whirlpoolApiCoordinator
                .getRpcSession()
                .withSorobanClient(
                    sorobanClient -> whirlpoolApiCoordinator.upStatusSend(sorobanClient, checkId),
                    sorobanUrl));
      } catch (Exception e) {
        if (log.isTraceEnabled()) {
          log.error("upStatusSend() failed: " + e.getMessage() + ", sorobanUrl=" + sorobanUrl);
        }
      }
    }

    // wait for propagation delay
    sleepOrchestrator(PROPAGATION_DELAY, true);

    // fetch each node
    Map<String, Collection<SorobanItemTyped>> resultsByNode =
        sorobanUrls.parallelStream()
            .collect(
                Collectors.toMap(
                    sorobanUrl -> sorobanUrl, sorobanUrl -> fetchSorobanNode(sorobanUrl, checkId)));

    // find max propagation
    int maxPropagations =
        resultsByNode.values().stream().mapToInt(list -> list.size()).max().getAsInt();

    // update UpStatus
    int nbUp = 0;
    for (Map.Entry<String, Collection<SorobanItemTyped>> e : resultsByNode.entrySet()) {
      String sorobanUrl = e.getKey();
      Collection<SorobanItemTyped> messages = e.getValue();
      String origins =
          messages.stream()
              .map(
                  i -> {
                    try {
                      return i.read(UpStatusMessage.class).origin;
                    } catch (Exception ee) {
                      log.error("read(UpStatusMessage) failed", ee);
                      return "null";
                    }
                  })
              .collect(Collectors.joining(", "));

      String info =
          messages.size() > 0 ? messages.size() + " nodes found: " + origins : "No node found";
      if (messages.size() == maxPropagations && maxPropagations >= MIN_PROPAGATION) {
        // UP
        upStatusPool.setStatusUp(sorobanUrl, info);
        nbUp++;
      } else {
        // DOWN
        upStatusPool.setStatusDown(sorobanUrl, info);
      }
    }

    // reset status
    boolean currentStatusAllDown = statusAllDown;
    String currentStatusDegradedMode = statusDegradedMode;
    int currentStatusNbUnsynchronized = statusNbUnsynchronized;
    statusAllDown = false;
    statusDegradedMode = null;
    statusNbUnsynchronized = 0;

    if (nbUp == 0) {
      if (maxPropagations > 0) {
        // use degraded mode: pick one single node as up and route all traffic to it
        String sorobanUrl = resultsByNode.keySet().iterator().next();
        log.error(
            "*** ALL SOROBAN NODES SEEMS UNSYNCHRONIZED! USING DEGRADED MODE ***, onion="
                + onion
                + ", sorobanUrl="
                + sorobanUrl);
        upStatusPool.setStatusUp(sorobanUrl, "*** DEGRADED MODE ***");
        statusDegradedMode = sorobanUrl;

        // notify monitoring once
        if (!statusDegradedMode.equals(currentStatusDegradedMode)) {
          String info =
              "Soroban cluster is KO (onion="
                  + onion
                  + "): 0/"
                  + sorobanUrls.size()
                  + " synchronized. Please check soroban cluster!";
          if (!onion) monitoringService.notifyWarning(info);
        }
      } else {
        log.error("*** ALL SOROBAN NODES SEEMS DOWN, onion=" + onion);
        statusAllDown = true;

        // notify monitoring once
        if (!currentStatusAllDown) {
          String info =
              "Soroban cluster is KO (onion="
                  + onion
                  + "): 0/"
                  + sorobanUrls.size()
                  + " synchronized. Redirecting clients to a single node: "
                  + statusDegradedMode
                  + " (onion="
                  + onion
                  + ")";
          if (!onion) monitoringService.notifyError(info);
        }
      }
    } else {
      log.info(
          nbUp
              + "/"
              + sorobanUrls.size()
              + " up, maxPropagations="
              + maxPropagations
              + ", onion="
              + onion);
      statusNbUnsynchronized = sorobanUrls.size() - nbUp;
      if (currentStatusNbUnsynchronized != statusNbUnsynchronized) {
        if (statusNbUnsynchronized == 0) {
          // all up
          String info =
              "Soroban cluster is OK (onion="
                  + onion
                  + "): "
                  + nbUp
                  + "/"
                  + sorobanUrls.size()
                  + " synchronized";
          if (!onion) monitoringService.notifySuccess(info);
        } else {
          // partially up
          String info =
              "Soroban cluster is partially OK (onion="
                  + onion
                  + "): "
                  + nbUp
                  + "/"
                  + sorobanUrls.size()
                  + " synchronized";
          if (!onion) monitoringService.notifyInfo(info);
        }
      }
    }
    int nbDown = sorobanUrls.size() - nbUp;
    this.status.put(onion, Pair.of(nbUp, nbDown));
  }

  // TODO
  private String computeResultId(Collection<SorobanItemTyped> items) {
    try {
      return items.stream()
          .map(
              i -> {
                try {
                  return i.read(UpStatusMessage.class).origin;
                } catch (Exception e) {
                  log.error("read(UpStatusMessage) failed", e);
                  return null;
                }
              })
          .filter(i -> i != null)
          .sorted()
          .collect(Collectors.joining("|"));
    } catch (Exception e) {
      log.error("computeResultId() failed", e);
      return "null";
    }
  }

  private Collection<SorobanItemTyped> fetchSorobanNode(String sorobanUrl, long checkId) {
    try {
      return asyncUtil.blockingGet(
          whirlpoolApiCoordinator
              .getRpcSession()
              .withSorobanClient(
                  sorobanClient -> whirlpoolApiCoordinator.upStatusFetch(sorobanClient, checkId),
                  sorobanUrl));
    } catch (Exception e) {
      log.error(
          "upStatusFetch("
              + sorobanUrl
              + ") failed: "
              + e.getMessage()
              + ", sorobanUrl="
              + sorobanUrl);
      return new LinkedList<>();
    }
  }

  public Pair<Integer, Integer> getNbUpDown(boolean onion) {
    return status.get(onion);
  }
}
