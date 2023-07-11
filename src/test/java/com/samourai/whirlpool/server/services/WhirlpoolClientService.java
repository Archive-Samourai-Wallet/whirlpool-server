package com.samourai.whirlpool.server.services;

import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClient;
import com.samourai.http.client.IHttpClientService;
import com.samourai.soroban.client.RpcWallet;
import com.samourai.stomp.client.IStompClientService;
import com.samourai.stomp.client.JettyStompClientService;
import com.samourai.tor.client.TorClientService;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.chain.ChainSupplier;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.soroban.SorobanClientApi;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.IndexRange;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
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

  @Autowired
  public WhirlpoolClientService(
      JavaHttpClientService httpClientService,
      WhirlpoolServerConfig serverConfig,
      BlockchainDataService blockchainDataService,
      RpcClientServiceServer rpcClientServiceServer) {
    this.httpClientService = httpClientService;
    this.serverConfig = serverConfig;
    this.blockchainDataService = blockchainDataService;
    this.rpcClientServiceServer = rpcClientServiceServer;
  }

  public WhirlpoolClientConfig createWhirlpoolClientConfig() {
    TorClientService torClientService =
        new TorClientService() {
          @Override
          public void changeIdentity() {}
        };
    IStompClientService stompClientService =
        new JettyStompClientService(
            httpClientService, WhirlpoolProtocol.HEADER_MESSAGE_TYPE, ClientUtils.USER_AGENT);
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
          stompClientService,
          torClientService,
          rpcClientServiceServer,
          new SorobanClientApi(serverConfig.getWhirlpoolNetwork()),
          Bip47UtilJava.getInstance(),
          null,
          serverConfig.getWhirlpoolNetwork(), // TODO String serverUrl = "http://127.0.0.1:" + port;
          IndexRange.FULL,
          false);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public MixParams computeMixParams(
      RpcWallet rpcWallet, Pool pool, UtxoWithBalance utxoWithBalance, ECKey ecKey) {
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
            rpcWallet);
    return mixParams;
  }
}
