package com.samourai.whirlpool.server.config;

import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.bip47.rpc.Bip47Partner;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.protocol.SorobanProtocolWhirlpool;
import com.samourai.whirlpool.protocol.soroban.api.WhirlpoolPartnerApiCoordinator;
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
  private String coordinatorWalletPaymentCodeSignature;
  private RpcSession rpcSession;
  private SorobanProtocolWhirlpool sorobanProtocolWhirlpool;

  public WhirlpoolServerContext(
      WhirlpoolServerConfig serverConfig,
      RpcClientServiceServer rpcClientServiceServer,
      SorobanProtocolWhirlpool sorobanProtocolWhirlpool)
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
    coordinatorWalletPaymentCodeSignature =
        Utils.signMessage(signingWallet, coordinatorWallet.getPaymentCode().toString());
    rpcSession =
        rpcClientServiceServer.getRpcWallet(coordinatorWallet.getBip47Wallet()).createRpcSession();
    this.sorobanProtocolWhirlpool = sorobanProtocolWhirlpool;

    log.info("coordinatorWallet: " + coordinatorWallet);
    log.info("signingWallet: " + signingWallet);
  }

  public Bip47Partner getBip47Partner(PaymentCode paymentCode) throws Exception {
    return rpcSession.getRpcWallet().getBip47Partner(paymentCode, false);
  }

  public WhirlpoolPartnerApiCoordinator getWhirlpoolPartnerApi(Bip47Partner bip47Partner) {
    return new WhirlpoolPartnerApiCoordinator(rpcSession, bip47Partner, sorobanProtocolWhirlpool);
  }

  public SecretWalletContext getCoordinatorWallet() {
    return coordinatorWallet;
  }

  public SecretWalletContext getSigningWallet() {
    return signingWallet;
  }

  public String getCoordinatorWalletPaymentCodeSignature() {
    return coordinatorWalletPaymentCodeSignature;
  }

  public RpcSession getRpcSession() {
    return rpcSession;
  }
}
