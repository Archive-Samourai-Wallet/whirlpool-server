package com.samourai.whirlpool.server.services;

import com.samourai.http.client.JettyHttpClientService;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.chain.ChainSupplier;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.crypto.CryptoUtil;
import com.samourai.wallet.httpClient.IHttpClientService;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.wallet.beans.IndexRange;
import com.samourai.whirlpool.client.wallet.data.coordinator.CoordinatorSupplier;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.SorobanAppWhirlpool;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.rpc.RpcClientServiceServer;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WhirlpoolClientService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private JavaHttpClientService httpClientService;
  private WhirlpoolServerConfig serverConfig;
  private BlockchainDataService blockchainDataService;
  private RpcClientServiceServer rpcClientServiceServer;
  private SorobanAppWhirlpool sorobanAppWhirlpool;
  private CryptoUtil cryptoUtil;

  @Autowired
  public WhirlpoolClientService(
      JavaHttpClientService httpClientService,
      WhirlpoolServerConfig serverConfig,
      BlockchainDataService blockchainDataService,
      RpcClientServiceServer rpcClientServiceServer,
      SorobanAppWhirlpool sorobanAppWhirlpool,
      CryptoUtil cryptoUtil) {
    this.httpClientService = httpClientService;
    this.serverConfig = serverConfig;
    this.blockchainDataService = blockchainDataService;
    this.rpcClientServiceServer = rpcClientServiceServer;
    this.sorobanAppWhirlpool = sorobanAppWhirlpool;
    this.cryptoUtil = cryptoUtil;
  }

  public WhirlpoolClientConfig createWhirlpoolClientConfig() {

    IHttpClientService httpClientService = new JettyHttpClientService();

    try {
      return new WhirlpoolClientConfig(
          httpClientService,
          rpcClientServiceServer,
          Bip47UtilJava.getInstance(),
          cryptoUtil,
          null,
          serverConfig.getSamouraiNetwork(),
          IndexRange.FULL,
          false);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public MixParams computeMixParams(
      Pool pool,
      UtxoWithBalance utxoWithBalance,
      ECKey ecKey,
      CoordinatorSupplier coordinatorSupplier) {
    IPremixHandler premixHandler = new PremixHandler(utxoWithBalance, ecKey, "healthCheck");
    IPostmixHandler postmixHandler =
        new IPostmixHandler() {
          @Override
          public MixDestination computeDestinationNext() throws Exception {
            return null;
          }

          @Override
          public MixDestination computeDestination(int index) throws Exception {
            return null;
          }

          @Override
          public void onRegisterOutput() {}

          @Override
          public void onMixFail() {}

          @Override
          public IIndexHandler getIndexHandler() {
            return null;
          }
        };
    ChainSupplier chainSupplier = blockchainDataService.computeChainSupplier();
    RpcSession rpcSession = rpcClientServiceServer.generateRpcWallet().createRpcSession();
    MixParams mixParams =
        new MixParams(
            pool.getPoolId(),
            pool.getDenomination(),
            pool.computeMustMixBalanceMin(),
            pool.computeMustMixBalanceMax(),
            null,
            premixHandler,
            postmixHandler,
            chainSupplier,
            coordinatorSupplier,
            rpcSession);
    return mixParams;
  }
}
