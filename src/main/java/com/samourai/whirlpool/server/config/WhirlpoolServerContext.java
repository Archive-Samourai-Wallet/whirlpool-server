package com.samourai.whirlpool.server.config;

import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.whirlpool.server.beans.SecretWalletContext;
import com.samourai.whirlpool.server.services.rpc.RpcClientServiceServer;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WhirlpoolServerContext {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private SecretWalletContext coordinatorWallet;
  private SecretWalletContext signingWallet;
  private String coordinatorSenderSignedBySigningAddress;
  private RpcSession rpcSession;

  public WhirlpoolServerContext(
      WhirlpoolServerConfig serverConfig, RpcClientServiceServer rpcClientServiceServer)
      throws Exception {
    NetworkParameters params = serverConfig.getNetworkParameters();
    try {
      coordinatorWallet = new SecretWalletContext(serverConfig.getCoordinatorWallet(), params);
    } catch (Exception e) {
      throw new Exception("Invalid configuration for server.coordinatorWallet", e);
    }
    try {
      signingWallet = new SecretWalletContext(serverConfig.getSigningWallet(), params);
    } catch (Exception e) {
      throw new Exception("Invalid configuration for server.signingWallet", e);
    }
    coordinatorSenderSignedBySigningAddress =
        Utils.signMessage(signingWallet, coordinatorWallet.getPaymentCode().toString());
    rpcSession =
        rpcClientServiceServer.getRpcWallet(coordinatorWallet.getBip47Account()).createRpcSession();

    log.info("coordinatorWallet: " + coordinatorWallet);
    log.info("signingWallet: " + signingWallet);
  }

  public SecretWalletContext getCoordinatorWallet() {
    return coordinatorWallet;
  }

  public SecretWalletContext getSigningWallet() {
    return signingWallet;
  }

  public String getCoordinatorSenderSignedBySigningAddress() {
    return coordinatorSenderSignedBySigningAddress;
  }

  public RpcSession getRpcSession() {
    return rpcSession;
  }
}
