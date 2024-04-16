package com.samourai.whirlpool.server.orchestrators;

import com.samourai.soroban.client.endpoint.meta.typed.SorobanItemTyped;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.httpClient.HttpUsage;
import com.samourai.wallet.sorobanClient.SorobanServerDex;
import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.wallet.util.Pair;
import com.samourai.wallet.util.urlStatus.UpStatusPool;
import com.samourai.whirlpool.protocol.soroban.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.monitoring.MonitoringService;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class SorobanUpStatusOrchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final AsyncUtil asyncUtil = AsyncUtil.getInstance();

  private static final int LOOP_DELAY = 90000; // every 1min30 after last loop
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
      MonitoringService monitoringService)
      throws Exception {
    super(LOOP_DELAY, 0, LOOP_DELAY / 1000);
    this.serverConfig = serverConfig;
    // use alternate identity to toggle Tor usage without affecting other connections
    this.whirlpoolApiCoordinator = whirlpoolApiCoordinator.createNewIdentity();
    this.monitoringService = monitoringService;

    this.status = new LinkedHashMap<>();
    this.status.put(true, Pair.of(0, 0));
    this.status.put(false, Pair.of(0, 0));

    // configure nodes from server config
    configureSorobanNodes();
  }

  private void configureSorobanNodes() {
    NetworkParameters params = serverConfig.getNetworkParameters();
    SorobanServerDex sorobanServerDex = SorobanServerDex.get(params);

    // read config
    Set<String> sorobanUrls = new LinkedHashSet<>(Arrays.asList(serverConfig.getSorobanNodes()));
    if (!sorobanUrls.isEmpty()) {
      Collection<String> sorobanUrlsClear = new LinkedList<>();
      Collection<String> sorobanUrlsOnion = new LinkedList<>();
      for (String sorobanUrl : sorobanUrls) {
        if (sorobanUrl.contains(".onion")) {
          sorobanUrlsOnion.add(sorobanUrl);
        } else {
          sorobanUrlsClear.add(sorobanUrl);
        }
      }

      // set active soroban nodes
      sorobanServerDex.setSorobanUrlsClear(sorobanUrlsClear);
      sorobanServerDex.setSorobanUrlsOnion(sorobanUrlsOnion);
    }

    Collection<String> sorobanUrlsClear = sorobanServerDex.getSorobanUrlsClear();
    Collection<String> sorobanUrlsOnion = sorobanServerDex.getSorobanUrlsOnion();
    log.info(
        "Configured "
            + sorobanUrlsClear.size()
            + " Soroban nodes for clearnet:\n * "
            + StringUtils.join(sorobanUrlsClear, "\n * "));
    log.info(
        "Configured "
            + sorobanUrlsOnion.size()
            + " Soroban nodes for onion:\n * "
            + StringUtils.join(sorobanUrlsOnion, "\n * "));
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
    // toggle Tor usage
    HttpUsage httpUsage = onion ? Utils.HTTPUSAGE_SOROBAN_ONION : HttpUsage.SOROBAN;
    String clusterInfo = onion ? "ONION" : "CLEARNET";
    whirlpoolApiCoordinator.getRpcSession().setHttpUsage(httpUsage);

    UpStatusPool upStatusPool = RpcSession.getUpStatusPool();
    long checkId = System.currentTimeMillis();

    // send one UpStatusMessage per soroban node
    Collection<String> sorobanUrls = computeSorobanUrls(onion);
    List<Pair<String, Boolean>> sendResults =
        sorobanUrls.parallelStream()
            .map(
                sorobanUrl -> { // use map to wait for each results
                  try {
                    asyncUtil.blockingAwait(
                        whirlpoolApiCoordinator
                            .getRpcSession()
                            .withSorobanClient(
                                sorobanClient ->
                                    whirlpoolApiCoordinator.upStatusSend(sorobanClient, checkId),
                                sorobanUrl));
                    return Pair.of(sorobanUrl, true);
                  } catch (Exception e) {
                    if (log.isTraceEnabled()) {
                      log.error(
                          "upStatusSend() failed: "
                              + e.getMessage()
                              + ", sorobanUrl="
                              + sorobanUrl);
                    }
                    return Pair.of(sorobanUrl, false);
                  }
                })
            .collect(Collectors.toList());
    // TODO use sendResults for skipping down nodes

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
    List<Pair<String, String>> downUrls = new LinkedList<>();
    for (Map.Entry<String, Collection<SorobanItemTyped>> e : resultsByNode.entrySet()) {
      String sorobanUrl = e.getKey();
      Collection<SorobanItemTyped> messages = e.getValue();
      /*String origins =
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
          .collect(Collectors.joining(", "));*/

      String info = messages.size() + "/" + sorobanUrls.size() + " synchronized";
      if (messages.size() == maxPropagations && maxPropagations >= MIN_PROPAGATION) {
        // UP
        upStatusPool.setStatusUp(sorobanUrl, info);
      } else {
        // DOWN
        upStatusPool.setStatusDown(sorobanUrl, info);
        downUrls.add(Pair.of(sorobanUrl, info));
      }
    }

    // reset status
    boolean currentStatusAllDown = statusAllDown;
    String currentStatusDegradedMode = statusDegradedMode;
    int currentStatusNbUnsynchronized = statusNbUnsynchronized;
    statusAllDown = false;
    statusDegradedMode = null;
    statusNbUnsynchronized = 0;

    int nbUp = sorobanUrls.size() - downUrls.size();
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
              "Soroban cluster "
                  + clusterInfo
                  + " is DEGRADED: 0/"
                  + sorobanUrls.size()
                  + " synchronized. Redirecting clients to a single node: "
                  + statusDegradedMode;
          monitoringService.notifyWarning(info);
        }
      } else {
        log.error("*** ALL SOROBAN NODES SEEMS DOWN, onion=" + onion);
        statusAllDown = true;

        // notify monitoring once
        if (!currentStatusAllDown) {
          String info =
              "Soroban cluster "
                  + clusterInfo
                  + " is DOWN: 0/"
                  + sorobanUrls.size()
                  + " synchronized. Please check soroban cluster!";
          monitoringService.notifyError(info);
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
      statusNbUnsynchronized = downUrls.size();
      if (currentStatusNbUnsynchronized != statusNbUnsynchronized) {
        if (statusNbUnsynchronized == 0) {
          // all up
          String info =
              "Soroban cluster "
                  + clusterInfo
                  + " is OK: "
                  + nbUp
                  + "/"
                  + sorobanUrls.size()
                  + " synchronized";
          monitoringService.notifySuccess(info);
        } else {
          // partially up
          String downUrlsStr =
              "\n * "
                  + downUrls.stream()
                      .map(p -> p.getLeft() + ": " + p.getRight())
                      .collect(Collectors.joining("\n * "));
          String info =
              "Soroban cluster "
                  + clusterInfo
                  + " is partially OK: "
                  + nbUp
                  + "/"
                  + sorobanUrls.size()
                  + " synchronized."
                  + downUrlsStr;
          monitoringService.notifyInfo(info);
        }
      }
    }
    int nbDown = downUrls.size();
    this.status.put(onion, Pair.of(nbUp, nbDown));

    setLastRun();
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
