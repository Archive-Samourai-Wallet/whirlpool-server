package com.samourai.whirlpool.server.config;

import com.samourai.javaserver.config.ServerServicesConfig;
import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.javawsserver.config.JWSSConfig;
import com.samourai.wallet.api.explorer.ExplorerApi;
import com.samourai.wallet.bip47.rpc.java.SecretPointFactoryJava;
import com.samourai.wallet.bip47.rpc.secretPoint.ISecretPointFactory;
import com.samourai.wallet.hd.HD_WalletFactoryGeneric;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.CryptoTestUtil;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.feeOpReturn.FeeOpReturnImplV0;
import com.samourai.whirlpool.protocol.feeOpReturn.FeeOpReturnImplV1;
import com.samourai.whirlpool.protocol.util.XorMask;
import com.samourai.whirlpool.server.services.JavaHttpClientService;
import com.samourai.xmanager.client.XManagerClient;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableCaching
@EnableScheduling
public class ServicesConfig extends ServerServicesConfig {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected WhirlpoolServerConfig whirlpoolServerConfig;

  public ServicesConfig(WhirlpoolServerConfig whirlpoolServerConfig) {
    super();
    this.whirlpoolServerConfig = whirlpoolServerConfig;
  }

  @Bean
  TaskExecutor taskExecutor() {
    return new SimpleAsyncTaskExecutor();
  }

  @Bean
  ServerUtils serverUtils() {
    return ServerUtils.getInstance();
  }

  @Bean
  WhirlpoolProtocol whirlpoolProtocol() {
    return new WhirlpoolProtocol();
  }

  @Bean
  ISecretPointFactory secretPointFactory() {
    return SecretPointFactoryJava.getInstance();
  }

  @Bean
  XorMask xorMask(ISecretPointFactory secretPointFactory) {
    return XorMask.getInstance(secretPointFactory);
  }

  @Bean
  FormatsUtilGeneric formatsUtilGeneric() {
    return FormatsUtilGeneric.getInstance();
  }

  @Bean
  Bech32UtilGeneric bech32UtilGeneric() {
    return Bech32UtilGeneric.getInstance();
  }

  @Bean
  HD_WalletFactoryGeneric hdWalletFactory() {
    return HD_WalletFactoryGeneric.getInstance();
  }

  @Bean
  MessageSignUtilGeneric messageSignUtil() {
    return MessageSignUtilGeneric.getInstance();
  }

  @Bean
  TxUtil txUtil() {
    return TxUtil.getInstance();
  }

  @Bean
  CryptoTestUtil cryptoTestUtil() {
    return CryptoTestUtil.getInstance();
  }

  @Bean
  ExplorerApi explorerApi(WhirlpoolServerConfig serverConfig) {
    return new ExplorerApi(serverConfig.isTestnet());
  }

  @Bean
  XManagerClient xManagerClient(
      WhirlpoolServerConfig serverConfig, JavaHttpClientService httpClient) {
    return new XManagerClient(httpClient.getHttpClient(), serverConfig.isTestnet(), false);
  }

  @Bean
  FeeOpReturnImplV0 feeOpReturnV0(XorMask xorMask) {
    return new FeeOpReturnImplV0(xorMask);
  }

  @Bean
  FeeOpReturnImplV1 feeOpReturnV1(XorMask xorMask) {
    return new FeeOpReturnImplV1(xorMask);
  }

  @Bean
  JWSSConfig jwssConfig() {
    String[] endpoints =
        new String[] {
          WhirlpoolEndpoint.WS_CONNECT,
          WhirlpoolEndpoint.WS_REGISTER_INPUT,
          WhirlpoolEndpoint.WS_CONFIRM_INPUT,
          WhirlpoolEndpoint.WS_REVEAL_OUTPUT,
          WhirlpoolEndpoint.WS_SIGNING
        };
    String WS_PREFIX = "/ws/";
    String WS_PREFIX_DESTINATION = "/topic/";
    return new JWSSConfig(
        endpoints,
        WhirlpoolProtocol.WS_PREFIX_USER_PRIVATE,
        WS_PREFIX,
        WS_PREFIX_DESTINATION, // NOT USED
        WhirlpoolProtocol.WS_PREFIX_USER_REPLY);
  }
}
