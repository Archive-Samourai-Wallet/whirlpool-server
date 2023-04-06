package com.samourai.whirlpool.server.services;

import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClient;
import com.samourai.http.client.IWhirlpoolHttpClientService;
import com.samourai.soroban.client.RpcWallet;
import com.samourai.soroban.client.rpc.RpcService;
import com.samourai.stomp.client.IStompClientService;
import com.samourai.stomp.client.JettyStompClientService;
import com.samourai.tor.client.TorClientService;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.chain.ChainSupplier;
import com.samourai.wallet.crypto.CryptoUtil;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.IndexRange;
import com.samourai.whirlpool.client.whirlpool.ServerApi;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WhirlpoolClientService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private JavaHttpClientService httpClientService;
  private WhirlpoolServerConfig serverConfig;
  private CryptoUtil cryptoUtil;
  private BlockchainDataService blockchainDataService;

  @Autowired
  public WhirlpoolClientService(
      JavaHttpClientService httpClientService,
      WhirlpoolServerConfig serverConfig,
      CryptoUtil cryptoUtil,
      BlockchainDataService blockchainDataService) {
    this.httpClientService = httpClientService;
    this.serverConfig = serverConfig;
    this.cryptoUtil = cryptoUtil;
    this.blockchainDataService = blockchainDataService;
  }

  public WhirlpoolClientConfig createWhirlpoolClientConfig(
      String serverUrl, NetworkParameters params) {
    TorClientService torClientService =
        new TorClientService() {
          @Override
          public void changeIdentity() {}
        };
    IStompClientService stompClientService =
        new JettyStompClientService(
            httpClientService, WhirlpoolProtocol.HEADER_MESSAGE_TYPE, ClientUtils.USER_AGENT);
    IWhirlpoolHttpClientService multiUsageHttpClientService =
        new IWhirlpoolHttpClientService() {
          @Override
          public IHttpClient getHttpClient(HttpUsage httpUsage) {
            return httpClientService.getHttpClient();
          }

          @Override
          public void stop() {
            httpClientService.stop();
          }
        };

    IHttpClient httpClient =
        multiUsageHttpClientService.getHttpClient(HttpUsage.COORDINATOR_WEBSOCKET);
    RpcService rpcService = new RpcService(httpClient, cryptoUtil, false);

    ServerApi serverApi =
        new ServerApi(
            serverUrl, httpClientService.getHttpClient(), httpClientService.getHttpClient());

    try {
      PaymentCode paymentCodeCoordinator = serverConfig.computeSigningPaymentCode();
      return new WhirlpoolClientConfig(
          multiUsageHttpClientService,
          stompClientService,
          torClientService,
          rpcService,
          serverApi,
          null,
          params,
          IndexRange.FULL,
          paymentCodeCoordinator);
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
