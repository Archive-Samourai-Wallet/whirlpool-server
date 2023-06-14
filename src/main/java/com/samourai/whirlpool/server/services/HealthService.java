package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.soroban.client.RpcWallet;
import com.samourai.soroban.client.RpcWalletImpl;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactoryGeneric;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolServer;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientImpl;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.server.beans.Pool;
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
  private static final String MOCK_SEED_WORDS = "all all all all all all all all all all all all";
  private static final String MOCK_SEED_PASSPHRASE = "all";
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private SimpUserRegistry simpUserRegistry;
  private String lastError;
  private WhirlpoolClientConfig whirlpoolClientConfig;
  private WhirlpoolClientService whirlpoolClientService;
  private PoolService poolService;
  private RpcWallet rpcWallet;

  @Autowired
  public HealthService(
      WhirlpoolServerConfig whirlpoolServerConfig,
      SimpUserRegistry simpUserRegistry,
      WhirlpoolClientService whirlpoolClientService,
      PoolService poolService,
      HD_WalletFactoryGeneric walletFactory)
      throws Exception {
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.simpUserRegistry = simpUserRegistry;
    this.lastError = null;
    this.whirlpoolClientService = whirlpoolClientService;
    this.poolService = poolService;
    this.whirlpoolClientConfig = null;

    HD_Wallet hdw84 =
        walletFactory.restoreWallet(
            MOCK_SEED_WORDS, MOCK_SEED_PASSPHRASE, whirlpoolServerConfig.getNetworkParameters());
    BIP47Wallet bip47Wallet = new BIP47Wallet(hdw84);
    this.rpcWallet = new RpcWalletImpl(bip47Wallet);
  }

  @Scheduled(fixedDelay = 120000)
  public void scheduleConnectCheck() {
    if (true) {
      return; // TODO temporarily disabled to adapt for DEX
    }
    try {
      // thread check
      int nbThreads = ServerUtils.getInstance().getThreads().size();
      if (nbThreads > 150) {
        log.warn("WARNING: TOO MANY THREADS! " + nbThreads + " threads running!");
        logThreads();
      }

      WhirlpoolClientConfig config = computeWhirlpoolClientConfig();
      WhirlpoolClient whirlpoolClient = new WhirlpoolClientImpl(config);
      Pool pool = poolService.getPools().iterator().next();
      UtxoWithBalance utxoWithBalance =
          new UtxoWithBalance(
              new Utxo(RegisterInputService.HEALTH_CHECK_UTXO, 0), pool.getDenomination());
      MixParams mixParams =
          whirlpoolClientService.computeMixParams(rpcWallet, pool, utxoWithBalance, new ECKey());
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

  private WhirlpoolClientConfig computeWhirlpoolClientConfig() throws Exception {
    if (whirlpoolClientConfig == null) {
      WhirlpoolServer whirlpoolServer =
          whirlpoolServerConfig.isTestnet() ? WhirlpoolServer.TESTNET : WhirlpoolServer.MAINNET;
      String serverUrl = whirlpoolServer.getServerUrlClear();
      NetworkParameters params = whirlpoolServerConfig.getNetworkParameters();
      whirlpoolClientConfig = whirlpoolClientService.createWhirlpoolClientConfig(serverUrl, params);
    }
    return whirlpoolClientConfig;
  }

  public String getLastError() {
    return lastError;
  }
}
