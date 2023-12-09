package com.samourai.whirlpool.server.services;

import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClient;
import com.samourai.http.client.IHttpClientService;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.tor.client.TorClientService;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.chain.ChainSupplier;
import com.samourai.wallet.crypto.CryptoUtil;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.wallet.beans.IndexRange;
import com.samourai.whirlpool.client.wallet.data.coordinator.CoordinatorSupplier;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.SorobanProtocolWhirlpool;
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
  private SorobanProtocolWhirlpool sorobanProtocolWhirlpool;
  private CryptoUtil cryptoUtil;

  @Autowired
  public WhirlpoolClientService(
      JavaHttpClientService httpClientService,
      WhirlpoolServerConfig serverConfig,
      BlockchainDataService blockchainDataService,
      RpcClientServiceServer rpcClientServiceServer,
      SorobanProtocolWhirlpool sorobanProtocolWhirlpool,
      CryptoUtil cryptoUtil) {
    this.httpClientService = httpClientService;
    this.serverConfig = serverConfig;
    this.blockchainDataService = blockchainDataService;
    this.rpcClientServiceServer = rpcClientServiceServer;
    this.sorobanProtocolWhirlpool = sorobanProtocolWhirlpool;
    this.cryptoUtil = cryptoUtil;
  }

  public WhirlpoolClientConfig createWhirlpoolClientConfig() {
    TorClientService torClientService =
        new TorClientService() {
          @Override
          public void changeIdentity() {}
        };
    IHttpClientService multiUsageHttpClientService =
        new IHttpClientService() {
          @Override
          public IHttpClient getHttpClient(HttpUsage httpUsage) {
            return httpClientService.getHttpClient();
          }

          @Override
          public void stop() {
            httpClientService.stop();
          }
        };

    try {
      return new WhirlpoolClientConfig(
          multiUsageHttpClientService,
          torClientService,
          rpcClientServiceServer,
          Bip47UtilJava.getInstance(),
          cryptoUtil,
          null,
          serverConfig.getWhirlpoolNetwork(), // TODO String serverUrl = "http://127.0.0.1:" + port;
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
          public MixDestination computeDestination() throws Exception {
            return null;
          }

          @Override
          public void onRegisterOutput() {}

          @Override
          public void onMixFail() {}

          @Override
          public MixDestination getDestination() {
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
