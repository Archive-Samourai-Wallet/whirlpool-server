package com.samourai.whirlpool.server.orchestrators;

import com.samourai.soroban.client.SorobanServerDex;
import com.samourai.soroban.client.UntypedPayloadWithSender;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.whirlpool.protocol.SorobanProtocolWhirlpool;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.function.Predicate;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SorobanUpStatusOrchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final AsyncUtil asyncUtil = AsyncUtil.getInstance();
  private static final int START_DELAY =
      30000; // 30s start delay - to run after SorobanCoordinatorOrchestrator
  private static final int LOOP_DELAY = 240000; // 4min
  private static final int LAG_TRESHOLD = 3;

  private WhirlpoolServerConfig serverConfig;
  private WhirlpoolServerContext serverContext;
  private RpcSession rpcSession;
  private SorobanProtocolWhirlpool sorobanProtocolWhirlpool;

  public SorobanUpStatusOrchestrator(
      WhirlpoolServerConfig serverConfig,
      WhirlpoolServerContext serverContext,
      RpcSession rpcSession,
      SorobanProtocolWhirlpool sorobanProtocolWhirlpool) {
    super(LOOP_DELAY, START_DELAY, null);
    this.serverConfig = serverConfig;
    this.serverContext = serverContext;
    this.rpcSession = rpcSession;
    this.sorobanProtocolWhirlpool = sorobanProtocolWhirlpool;
  }

  private Collection<String> getServerUrls() {
    NetworkParameters params = serverConfig.getNetworkParameters();
    SorobanServerDex sorobanServerDex = SorobanServerDex.get(params);
    Collection<String> serverUrls = new LinkedList<>(sorobanServerDex.getServerUrls(false));
    serverUrls.addAll(sorobanServerDex.getServerUrls(true));
    return serverUrls;
  }

  @Override
  protected void runOrchestrator() {
    // check soroban statuses
    Collection<String> serverUrls = getServerUrls();
    Map<String, Long> lagByNode = checkSorobanNodes(serverUrls);
    if (log.isDebugEnabled()) {
      log.debug(
          "SorobanUpStatus: "
              + lagByNode.size()
              + "/"
              + serverUrls.size()
              + " downs: "
              + lagByNode.toString());
    }
  }

  private Map<String, Long> checkSorobanNodes(Collection<String> serverUrls) {
    Map<String, Long> lagByNode = new LinkedHashMap<>();
    String pCodeMine = serverContext.getCoordinatorWallet().getPaymentCode().toString();
    String dir = sorobanProtocolWhirlpool.getDirCoordinators();
    serverUrls
        .parallelStream()
        .forEach(
            serverUrl -> {
              try {
                Predicate<UntypedPayloadWithSender> filterPayloadsMine =
                    payload -> payload.getSender().toString().equals(pCodeMine);
                UntypedPayloadWithSender myLastPayload =
                    asyncUtil
                        .blockingGet(
                            rpcSession.withSorobanClient(
                                sorobanClient ->
                                    sorobanClient.listSignedWithSender(dir, filterPayloadsMine),
                                serverUrl))
                        .getFirst()
                        .orElseThrow(() -> new Exception("No payload of mine found"));

                // compute lagTime = number of missed SorobanCoordinatorOrchestrator's cycles
                Long lag =
                    (long) Math.floor(System.currentTimeMillis() - myLastPayload.getTimePayload())
                        / SorobanCoordinatorOrchestrator.LOOP_DELAY;
                if (lag > LAG_TRESHOLD) {
                  lagByNode.put(serverUrl, lag);
                  RpcSession.getUpStatusPool()
                      .setStatusDown(serverUrl, new Exception("lag detected: " + lag));
                }
              } catch (Exception e) {
                // upStatus will be automatically updated by withServerUrl() for TimeoutException
                lagByNode.put(serverUrl, 99999L);
              }
            });
    return lagByNode;
  }
}
