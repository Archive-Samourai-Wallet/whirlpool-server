package com.samourai.whirlpool.server.integration;

import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClient;
import com.samourai.http.client.IHttpClientService;
import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.wallet.api.backend.BackendServer;
import com.samourai.wallet.bip47.rpc.BIP47Account;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactoryGeneric;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.CryptoTestUtil;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.cli.config.CliConfig;
import com.samourai.whirlpool.client.utils.ClientCryptoService;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.data.dataSource.DataSourceFactory;
import com.samourai.whirlpool.client.wallet.data.dataSource.DojoDataSourceFactory;
import com.samourai.whirlpool.protocol.util.XorMask;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.PoolMinerFee;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.services.*;
import com.samourai.whirlpool.server.services.rpc.MockRpcClientServiceImpl;
import com.samourai.whirlpool.server.utils.AssertMultiClientManager;
import com.samourai.whirlpool.server.utils.TestUtils;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.TestNet3Params;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles(ServerUtils.PROFILE_TEST)
public abstract class AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @LocalServerPort protected int port;

  @Autowired protected WhirlpoolServerConfig serverConfig;

  @Autowired protected CryptoService cryptoService;

  protected ClientCryptoService clientCryptoService = new ClientCryptoService();

  @Autowired protected DbService dbService;

  @Autowired protected PoolService poolService;

  @Autowired protected MixService mixService;

  @Autowired protected InputValidationService inputValidationService;

  @Autowired protected ScodeService scodeService;

  @Autowired protected BlockchainDataService blockchainDataService;

  @Autowired protected MockRpcClientServiceImpl rpcClientService;

  @Autowired protected TestUtils testUtils;

  @Autowired protected Bech32UtilGeneric bech32Util;

  @Autowired protected HD_WalletFactoryGeneric walletFactory;

  protected Bip47UtilJava bip47Util = Bip47UtilJava.getInstance();

  @Autowired protected FormatsUtilGeneric formatsUtil;

  @Autowired protected TxUtil txUtil;

  @Autowired protected TaskExecutor taskExecutor;

  @Autowired protected Tx0ValidationService tx0ValidationService;

  @Autowired protected CacheService cacheService;

  @Autowired protected CryptoTestUtil cryptoTestUtil;

  @Autowired protected HD_WalletFactoryGeneric hdWalletFactory;

  @Autowired protected BlameService blameService;

  @Autowired protected FeePayloadService feePayloadService;
  @Autowired protected PushService pushService;
  @Autowired protected XorMask xorMask;

  protected MessageSignUtilGeneric messageSignUtil = MessageSignUtilGeneric.getInstance();

  protected MixLimitsService mixLimitsService;

  private AssertMultiClientManager multiClientManager;

  protected NetworkParameters params;

  protected CliConfig cliConfig;

  @BeforeEach
  public void setUp() throws Exception {
    // enable debug
    Utils.setLoggerDebug();

    serverConfig.validate();

    Assertions.assertTrue(
        MockRpcClientServiceImpl.class.isAssignableFrom(rpcClientService.getClass()));
    this.params = cryptoService.getNetworkParameters();

    cliConfig = new CliConfig(null, null);

    messageSignUtil = MessageSignUtilGeneric.getInstance();

    mixService.__setCONFIRM_INPUT_CHECK_DELAY(0);

    dbService.__reset();
    mixLimitsService = mixService.__getMixLimitsService();
    rpcClientService.resetMock();

    configurePools(serverConfig.getMinerFees(), serverConfig.getPools());
    cacheService._reset();
  }

  protected void configurePools(
      WhirlpoolServerConfig.MinerFeeConfig globalMinerFeeConfig,
      WhirlpoolServerConfig.PoolConfig... poolConfigs) {
    poolService.__reset(poolConfigs, globalMinerFeeConfig);
    mixService.__reset();
  }

  protected void configurePool(PoolMinerFee minerFee, WhirlpoolServerConfig.PoolConfig poolConfig) {
    poolService.__reset(poolConfig, minerFee);
    mixService.__reset();
  }

  protected Mix __nextMix(PoolMinerFee minerFee, WhirlpoolServerConfig.PoolConfig poolConfig)
      throws IllegalInputException {
    configurePool(minerFee, poolConfig);
    Pool pool = poolService.getPool(poolConfig.getId());
    Mix mix = mixService.__nextMix(pool);
    return mix;
  }

  protected Mix __nextMix(
      long denomination,
      long feeValue,
      long minerFeeMin,
      long minerFeeCap,
      long minerFeeMax,
      long minRelayFee,
      int mustMixMin,
      int liquidityMin,
      int anonymitySet)
      throws IllegalInputException {

    // find pool
    Optional<Pool> poolOpt =
        poolService.getPools().stream()
            .filter(p -> p.getDenomination() == denomination)
            .findFirst();
    String poolId = poolOpt.isPresent() ? poolOpt.get().getPoolId() : "pool-" + denomination;
    if (log.isDebugEnabled()) {
      log.debug(
          "+ __nextMix: " + (poolOpt.isPresent() ? "updating" : "creating") + " poolId=" + poolId);
    }

    // create/update pool config
    WhirlpoolServerConfig.PoolConfig poolConfig = new WhirlpoolServerConfig.PoolConfig();
    poolConfig.setId(poolId);
    poolConfig.setFeeValue(feeValue);
    poolConfig.setDenomination(denomination);
    poolConfig.setMustMixMin(mustMixMin);
    poolConfig.setLiquidityMin(liquidityMin);
    poolConfig.setAnonymitySet(anonymitySet);

    WhirlpoolServerConfig.MinerFeeConfig globalMinerFeeConfig =
        new WhirlpoolServerConfig.MinerFeeConfig();
    globalMinerFeeConfig.setMinerFeeMin(minerFeeMin);
    globalMinerFeeConfig.setMinerFeeCap(minerFeeCap);
    globalMinerFeeConfig.setMinerFeeMax(minerFeeMax);
    globalMinerFeeConfig.setMinRelayFee(minRelayFee);

    PoolMinerFee minerFee = new PoolMinerFee(globalMinerFeeConfig, null, mustMixMin);

    // run new mix for the pool
    return __nextMix(minerFee, poolConfig);
  }

  protected Mix __nextMix(int mustMixMin, int liquidityMin, int anonymitySet, Pool copyPool)
      throws IllegalInputException {
    // create new pool
    WhirlpoolServerConfig.PoolConfig poolConfig = new WhirlpoolServerConfig.PoolConfig();
    poolConfig.setId(Utils.generateUniqueString());
    poolConfig.setDenomination(copyPool.getDenomination());
    poolConfig.setFeeValue(copyPool.getPoolFee().getFeeValue());
    poolConfig.setFeeAccept(copyPool.getPoolFee().getFeeAccept());
    poolConfig.setMustMixMin(mustMixMin);
    poolConfig.setLiquidityMin(liquidityMin);
    poolConfig.setAnonymitySet(anonymitySet);

    // run new mix for the pool
    return __nextMix(copyPool.getMinerFee(), poolConfig);
  }

  protected Mix __getCurrentMix() {
    Pool pool = poolService.getPools().iterator().next();
    return pool.getCurrentMix();
  }

  @AfterEach
  public void tearDown() {
    if (multiClientManager != null) {
      multiClientManager.exit();
    }
  }

  protected AssertMultiClientManager multiClientManager(int nbClients, Mix mix) {
    multiClientManager =
        new AssertMultiClientManager(
            nbClients,
            mix,
            testUtils,
            cryptoService,
            rpcClientService,
            blockchainDataService,
            port,
            params);
    return multiClientManager;
  }

  public TxOutPoint createAndMockTxOutPoint(
      SegwitAddress address, long amount, Integer nbConfirmations, Integer utxoIndex)
      throws Exception {

    if (utxoIndex == null) {
      utxoIndex = 0;
    }
    Integer nbOuts = utxoIndex + 1;
    RpcTransaction rpcTransaction =
        rpcClientService.createAndMockTx(address, amount, nbConfirmations, nbOuts);

    TxOutPoint txOutPoint = blockchainDataService.getOutPoint(rpcTransaction, utxoIndex).get();
    return txOutPoint;
  }

  public TxOutPoint createAndMockTxOutPoint(SegwitAddress address, long amount) throws Exception {
    return createAndMockTxOutPoint(address, amount, null, null);
  }

  public TxOutPoint createAndMockTxOutPoint(SegwitAddress address, long amount, int nbConfirmations)
      throws Exception {
    return createAndMockTxOutPoint(address, amount, nbConfirmations, null);
  }

  protected void setScodeConfig(String scode, short payload, int feeValuePercent, Long expiration) {
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig =
        new WhirlpoolServerConfig.ScodeSamouraiFeeConfig();
    scodeConfig.setPayload(payload);
    scodeConfig.setFeeValuePercent(feeValuePercent);
    if (expiration != null) {
      scodeConfig.setExpiration(expiration);
    }
    serverConfig.getSamouraiFees().getScodes().put(scode, scodeConfig);
    serverConfig
        .getSamouraiFees()
        .setScodes(serverConfig.getSamouraiFees().getScodes()); // reset scodesUpperCase
  }

  protected WhirlpoolWalletConfig computeWhirlpoolWalletConfig() {
    DataSourceFactory dataSourceFactory =
        new DojoDataSourceFactory(BackendServer.TESTNET, false, null);
    IHttpClientService httpClientService =
        new IHttpClientService() {
          @Override
          public IHttpClient getHttpClient(HttpUsage httpUsage) {
            return null;
          }

          @Override
          public void stop() {}
        };
    WhirlpoolWalletConfig config =
        new WhirlpoolWalletConfig(
            dataSourceFactory, httpClientService, null, null, null, TestNet3Params.get(), false);
    return config;
  }

  protected void waitMixLimitsService(Mix mix) throws Exception {
    mixLimitsService.__simulateElapsedTime(mix, 999999999);
    synchronized (this) {
      wait(500);
    }
  }

  protected BIP47Account computeBip47Account() throws Exception {
    WhirlpoolServerConfig.SecretWalletConfig secretWalletConfig =
        serverConfig.getSamouraiFees().getSecretWallet();
    HD_Wallet bip44wallet =
        hdWalletFactory.restoreWallet(
            secretWalletConfig.getWords(), secretWalletConfig.getPassphrase(), params);
    BIP47Wallet bip47Wallet = new BIP47Wallet(bip44wallet);
    BIP47Account bip47Account = bip47Wallet.getAccount(0);
    String PCODE =
        "PM8TJVGXADoSSFmre2HstFraDFYT35K7ccGLMoLMkKS5xMSooWe6RYJBsjqic77EyLs9ULP5unaCajCA2VNVjvETQqyoDEF59dcyGL1riWbk9AwNfAN1";
    Assertions.assertEquals(PCODE, bip47Account.getPaymentCode());
    return bip47Account;
  }

  protected BIP47Account computeBip47AccountV0() throws Exception {
    WhirlpoolServerConfig.SecretWalletConfig secretWalletConfig =
        serverConfig.getSamouraiFees().getSecretWalletV0();
    HD_Wallet bip44wallet =
        hdWalletFactory.restoreWallet(
            secretWalletConfig.getWords(), secretWalletConfig.getPassphrase(), params);
    BIP47Wallet bip47Wallet = new BIP47Wallet(bip44wallet);
    BIP47Account bip47Account = bip47Wallet.getAccount(0);
    String PCODE =
        "PM8TJXp19gCE6hQzqRi719FGJzF6AreRwvoQKLRnQ7dpgaakakFns22jHUqhtPQWmfevPQRCyfFbdDrKvrfw9oZv5PjaCerQMa3BKkPyUf9yN1CDR3w6";
    Assertions.assertEquals(PCODE, bip47Account.getPaymentCode());
    return bip47Account;
  }

  protected TransactionOutput mockTxOutput(SegwitAddress address) throws Exception {
    // generate transaction with bitcoinj
    Transaction transaction = new Transaction(params);

    // add outputs
    String addressBech32 = address.getBech32AsString();
    TransactionOutput transactionOutput =
        bech32Util.getTransactionOutput(addressBech32, 1234, params);
    transaction.addOutput(transactionOutput);

    // add coinbase input
    int txCounter = 1;
    TransactionInput transactionInput =
        new TransactionInput(
            params, transaction, new byte[] {(byte) txCounter, (byte) (txCounter++ >> 8)});
    transaction.addInput(transactionInput);
    return transactionOutput;
  }
}
