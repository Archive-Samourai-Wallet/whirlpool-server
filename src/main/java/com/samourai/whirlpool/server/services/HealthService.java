package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolServer;
import com.samourai.whirlpool.client.wallet.data.chain.ChainSupplier;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientImpl;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private SimpUserRegistry simpUserRegistry;
  private String lastError;
  private WhirlpoolClientConfig whirlpoolClientConfig;
  private BlockchainDataService blockchainDataService;
  private WhirlpoolClientService whirlpoolClientService;

  @Autowired
  public HealthService(
      WhirlpoolServerConfig whirlpoolServerConfig,
      SimpUserRegistry simpUserRegistry,
      BlockchainDataService blockchainDataService,
      WhirlpoolClientService whirlpoolClientService) {
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.simpUserRegistry = simpUserRegistry;
    this.blockchainDataService = blockchainDataService;
    this.lastError = null;
    this.whirlpoolClientService = whirlpoolClientService;
    this.whirlpoolClientConfig = null;
  }

  @Scheduled(fixedDelay = 120000)
  public void scheduleConnectCheck() {
    try {
      // thread check
      int nbThreads = ServerUtils.getInstance().getThreads().size();
      if (nbThreads > 150) {
        log.warn("WARNING: TOO MANY THREADS! " + nbThreads + " threads running!");
        logThreads();
      }

      WhirlpoolClientConfig config = computeWhirlpoolClientConfig();
      WhirlpoolClient whirlpoolClient = new WhirlpoolClientImpl(config);
      MixParams mixParams = computeMixParams();
      WhirlpoolClientListener listener =
          new WhirlpoolClientListener() {
            @Override
            public void success(Utxo receiveUtxo) {}

            @Override
            public void progress(MixStep mixStep) {}

            @Override
            public void fail(MixFailReason reason, String notifiableError) {
              if (notifiableError.equals(RegisterInputService.HEALTH_CHECK_SUCCESS)) {
                // expected response
                if (log.isTraceEnabled()) {
                  log.trace("healthCheck SUCCESS");
                }
                lastError = null;
              } else {
                // unexpected error
                log.error("healthCheck ERROR: " + notifiableError);
                log.info("Active users: " + simpUserRegistry.getUserCount());
                logThreads();
                lastError = notifiableError;
              }
            }
          };
      whirlpoolClient.whirlpool(mixParams, listener);
    } catch (Exception e) {
      log.error("healthCheck ERROR", e);
      lastError = e.getMessage();
    }
  }

  private void logThreads() {
    int i = 0;
    Collection<Thread> threads = ServerUtils.getInstance().getThreads();
    for (Thread thread : threads) {
      String stackTrace =
          Thread.State.BLOCKED.equals(thread.getState())
              ? StringUtils.join(thread.getStackTrace(), "\n")
              : "";
      log.info(
          "Thread #" + i + " " + thread.getName() + " " + thread.getState() + ": " + stackTrace);
      i++;
    }
  }

  private WhirlpoolClientConfig computeWhirlpoolClientConfig() {
    if (whirlpoolClientConfig == null) {
      WhirlpoolServer whirlpoolServer =
          whirlpoolServerConfig.isTestnet() ? WhirlpoolServer.TESTNET : WhirlpoolServer.MAINNET;
      String serverUrl = whirlpoolServer.getServerUrlClear();
      NetworkParameters params = whirlpoolServerConfig.getNetworkParameters();
      whirlpoolClientConfig = whirlpoolClientService.createWhirlpoolClientConfig(serverUrl, params);
    }
    return whirlpoolClientConfig;
  }

  private MixParams computeMixParams() {
    WhirlpoolServerConfig.PoolConfig poolConfig = whirlpoolServerConfig.getPools()[0];
    UtxoWithBalance utxoWithBalance =
        new UtxoWithBalance(
            new Utxo(RegisterInputService.HEALTH_CHECK_UTXO, 0), poolConfig.getDenomination());
    IPremixHandler premixHandler = new PremixHandler(utxoWithBalance, new ECKey(), "healthCheck");
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
            poolConfig.getId(),
            poolConfig.getDenomination(),
            null,
            premixHandler,
            postmixHandler,
            chainSupplier);
    return mixParams;
  }

  public String getLastError() {
    return lastError;
  }
}
