package com.samourai.whirlpool.server.orchestrators;

import com.samourai.soroban.client.SorobanServerDex;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedList;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SorobanUpStatusOrchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final AsyncUtil asyncUtil = AsyncUtil.getInstance();
  private static final int LOOP_DELAY = 24000; // 4min

  private WhirlpoolServerConfig serverConfig;
  private RpcSession rpcSession;

  public SorobanUpStatusOrchestrator(WhirlpoolServerConfig serverConfig, RpcSession rpcSession) {
    super(LOOP_DELAY, 0, null);
    this.serverConfig = serverConfig;
    this.rpcSession = rpcSession;
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
    if (log.isDebugEnabled()) {
      log.debug("Checking " + serverUrls.size() + " soroban status...");
    }
    String dir = WhirlpoolProtocol.getSorobanDirCoordinators(serverConfig.getWhirlpoolNetwork());
    serverUrls
        .parallelStream()
        .forEach(
            serverUrl -> {
              try {
                String[] entries =
                    asyncUtil.blockingGet(
                        rpcSession.withRpcClient(
                            rpcClient -> rpcClient.directoryValues(dir), serverUrl));
                if (entries.length == 0) {
                  throw new Exception("Soroban server seems unsynchronized");
                }
              } catch (Exception e) {
                // upStatus will be automatically updated by withRpcClient()
              }
            });
  }
}
