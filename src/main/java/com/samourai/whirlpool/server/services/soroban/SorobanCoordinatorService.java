package com.samourai.whirlpool.server.services.soroban;

import com.samourai.soroban.client.RpcWallet;
import com.samourai.soroban.client.RpcWalletImpl;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.crypto.CryptoUtil;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.orchestrators.SorobanCoordinatorOrchestrator;
import com.samourai.whirlpool.server.orchestrators.SorobanInputOrchestrator;
import com.samourai.whirlpool.server.services.MinerFeeService;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.services.rpc.RpcClientServiceServer;
import com.samourai.whirlpool.server.utils.Utils;
import io.reactivex.Single;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SorobanCoordinatorService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private SorobanCoordinatorApi sorobanCoordinatorApi;
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private RpcWallet rpcWallet;
  private RpcSession rpcSession;

  private SorobanCoordinatorOrchestrator coordinatorOrchestrator;
  private SorobanInputOrchestrator inputOrchestrator;

  @Autowired
  public SorobanCoordinatorService(
      PoolService poolService,
      SorobanCoordinatorApi sorobanCoordinatorApi,
      WhirlpoolServerConfig whirlpoolServerConfig,
      RegisterInputService registerInputService,
      MinerFeeService minerFeeService,
      RpcClientServiceServer rpcClientServiceServer)
      throws Exception {
    this.sorobanCoordinatorApi = sorobanCoordinatorApi;
    this.whirlpoolServerConfig = whirlpoolServerConfig;

    // instanciate rpcClient
    NetworkParameters params = whirlpoolServerConfig.getNetworkParameters();
    BIP47Wallet bip47Wallet =
        Utils.computeSigningBip47Wallet(whirlpoolServerConfig.getSigningWallet(), params);
    CryptoUtil cryptoUtil = CryptoUtil.getInstanceJava();
    this.rpcWallet = new RpcWalletImpl(bip47Wallet, cryptoUtil);
    ECKey authenticationKey =
        Utils.computeSigningAddress(whirlpoolServerConfig.getSigningWallet(), params).getECKey();
    this.rpcSession = rpcClientServiceServer.getRpcSession("coordinator", authenticationKey);

    // start publishing pools
    coordinatorOrchestrator =
        new SorobanCoordinatorOrchestrator(
            whirlpoolServerConfig,
            poolService,
            minerFeeService,
            sorobanCoordinatorApi,
            rpcSession,
            rpcWallet);
    coordinatorOrchestrator.start(true);

    // start watching for Soroban inputs
    inputOrchestrator =
        new SorobanInputOrchestrator(
            poolService, sorobanCoordinatorApi, registerInputService, rpcSession, rpcWallet);
    inputOrchestrator.start(true);
  }

  public Single<String> inviteToMix(RegisteredInput registeredInput, Mix mix) throws Exception {
    return rpcSession
        .withRpcClientEncrypted(
            rpcWallet.getEncrypter(),
            rce ->
                // invite input
                sorobanCoordinatorApi.inviteToMix(
                    rce,
                    registeredInput,
                    mix,
                    whirlpoolServerConfig.getExternalUrlClear(),
                    whirlpoolServerConfig.getExternalUrlOnion(),
                    rpcWallet))
        // unregister input
        .doAfterSuccess(
            invitePayload ->
                rpcSession.withRpcClient(
                    rpcClient ->
                        sorobanCoordinatorApi
                            .unregisterInput(rpcClient, registeredInput)
                            .subscribe()));
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
}
