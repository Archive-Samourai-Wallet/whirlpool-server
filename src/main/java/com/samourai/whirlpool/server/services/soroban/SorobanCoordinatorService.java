package com.samourai.whirlpool.server.services.soroban;

import com.samourai.soroban.client.RpcWallet;
import com.samourai.soroban.client.RpcWalletImpl;
import com.samourai.soroban.client.rpc.RpcClientEncrypted;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.orchestrators.SorobanPoolInfoOrchestrator;
import com.samourai.whirlpool.server.orchestrators.SorobanRegisterInputOrchestrator;
import com.samourai.whirlpool.server.services.MinerFeeService;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.services.rpc.RpcClientServiceServer;
import com.samourai.whirlpool.server.utils.Utils;
import io.reactivex.Completable;
import java.lang.invoke.MethodHandles;
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
  private RpcClientEncrypted rpcClient;

  private SorobanPoolInfoOrchestrator poolInfoOrchestrator;
  private SorobanRegisterInputOrchestrator registerInputOrchestrator;

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
    this.rpcWallet = new RpcWalletImpl(bip47Wallet);
    this.rpcClient =
        rpcClientServiceServer.getRpcClient("coordinator").createRpcClientEncrypted(rpcWallet);

    // start publishing pools
    poolInfoOrchestrator =
        new SorobanPoolInfoOrchestrator(
            poolService, minerFeeService, sorobanCoordinatorApi, rpcClient);
    poolInfoOrchestrator.start(true);

    // start watching for Soroban inputs
    registerInputOrchestrator =
        new SorobanRegisterInputOrchestrator(
            poolService, sorobanCoordinatorApi, registerInputService, rpcClient);
    registerInputOrchestrator.start(true);
  }

  public Completable inviteToMix(RegisteredInput registeredInput, Mix mix) throws Exception {
    String coordinatorIp = whirlpoolServerConfig.getExternalIp();
    PaymentCode paymentCodeCoordinator = rpcWallet.getPaymentCode();
    return sorobanCoordinatorApi.inviteToMix(
        rpcClient, registeredInput, mix, coordinatorIp, paymentCodeCoordinator);
  }

  public void stop() {
    registerInputOrchestrator.stop();
    poolInfoOrchestrator.stop();
  }

  public SorobanPoolInfoOrchestrator _getPoolInfoOrchestrator() { // for tests
    return poolInfoOrchestrator;
  }

  public SorobanRegisterInputOrchestrator _getRegisterInputOrchestrator() { // for tests
    return registerInputOrchestrator;
  }
}
