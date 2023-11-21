package com.samourai.whirlpool.server.services.soroban;

import com.samourai.soroban.client.RpcWallet;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.whirlpool.protocol.WhirlpoolProtocolSoroban;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.SecretWalletContext;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.orchestrators.SorobanCoordinatorOrchestrator;
import com.samourai.whirlpool.server.orchestrators.SorobanInputOrchestrator;
import com.samourai.whirlpool.server.orchestrators.SorobanUpStatusOrchestrator;
import com.samourai.whirlpool.server.services.MinerFeeService;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.services.rpc.RpcClientServiceServer;
import io.reactivex.Completable;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SorobanCoordinatorService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private SorobanCoordinatorApi sorobanCoordinatorApi;
  private WhirlpoolServerConfig serverConfig;
  private RpcWallet rpcWallet;
  private RpcSession rpcSession;

  private SorobanUpStatusOrchestrator sorobanUpStatusOrchestrator;
  private SorobanCoordinatorOrchestrator coordinatorOrchestrator;
  private SorobanInputOrchestrator inputOrchestrator;

  @Autowired
  public SorobanCoordinatorService(
      PoolService poolService,
      SorobanCoordinatorApi sorobanCoordinatorApi,
      WhirlpoolServerConfig serverConfig,
      WhirlpoolServerContext serverContext,
      RegisterInputService registerInputService,
      MinerFeeService minerFeeService,
      RpcClientServiceServer rpcClientServiceServer)
      throws Exception {
    this.sorobanCoordinatorApi = sorobanCoordinatorApi;
    this.serverConfig = serverConfig;

    // instanciate rpcClient with coordinatorWallet
    SecretWalletContext coordinatorWallet = serverContext.getCoordinatorWallet();
    this.rpcWallet = rpcClientServiceServer.getRpcWallet(coordinatorWallet.getBip47Wallet());
    this.rpcSession = rpcClientServiceServer.createRpcSession(rpcWallet);

    // SecretWalletContext signingWallet = serverContext.getSigningWallet();
    // ECKey authenticationKey = signingWallet.getAddress().getECKey();
    // this.rpcSession.setAuthenticationKey(authenticationKey);

    // start watching soroban statuses
    WhirlpoolProtocolSoroban whirlpoolProtocolSoroban = sorobanCoordinatorApi.getWhirlpoolProtocolSoroban();
    sorobanUpStatusOrchestrator =
        new SorobanUpStatusOrchestrator(serverConfig, rpcSession, whirlpoolProtocolSoroban);
    sorobanUpStatusOrchestrator.start(true);

    // start publishing pools
    coordinatorOrchestrator =
        new SorobanCoordinatorOrchestrator(
            serverConfig,
            serverContext,
            poolService,
            minerFeeService,
            sorobanCoordinatorApi,
            rpcSession);
    coordinatorOrchestrator.start(true);

    // start watching for Soroban inputs
    inputOrchestrator =
        new SorobanInputOrchestrator(
            poolService, sorobanCoordinatorApi, registerInputService, rpcSession, rpcWallet);
    inputOrchestrator.start(true);
  }

  public Completable inviteToMix(RegisteredInput registeredInput, Mix mix) throws Exception {
    return rpcSession.withRpcClientEncrypted(
        rce ->
            // invite input
            sorobanCoordinatorApi.inviteToMix(
                rce,
                registeredInput,
                mix,
                serverConfig.getExternalUrlClear(),
                serverConfig.getExternalUrlOnion(),
                rpcWallet));
  }

  public void stop() {
    inputOrchestrator.stop();
    coordinatorOrchestrator.stop();
  }

  public SorobanCoordinatorOrchestrator _getCoordinatorOrchestrator() { // for tests
    return coordinatorOrchestrator;
  }

  public SorobanInputOrchestrator _getInputOrchestrator() { // for tests
    return inputOrchestrator;
  }

  public RpcSession getRpcSession() {
    return rpcSession;
  }
}
