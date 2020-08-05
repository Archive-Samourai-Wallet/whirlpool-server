package com.samourai.whirlpool.server.integration;

import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.hd.java.HD_WalletFactoryJava;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.CryptoTestUtil;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.cli.config.CliConfig;
import com.samourai.whirlpool.client.utils.ClientCryptoService;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
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
import org.bitcoinj.core.NetworkParameters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.rules.ExpectedException;
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

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Autowired protected WhirlpoolServerConfig serverConfig;

  @Autowired protected CryptoService cryptoService;

  protected ClientCryptoService clientCryptoService = new ClientCryptoService();

  @Autowired protected DbService dbService;

  @Autowired protected PoolService poolService;

  @Autowired protected MixService mixService;

  @Autowired protected InputValidationService inputValidationService;

  @Autowired protected BlockchainDataService blockchainDataService;

  @Autowired protected MockRpcClientServiceImpl rpcClientService;

  @Autowired protected TestUtils testUtils;

  @Autowired protected Bech32UtilGeneric bech32Util;

  @Autowired protected HD_WalletFactoryJava walletFactory;

  protected Bip47UtilJava bip47Util = Bip47UtilJava.getInstance();

  @Autowired protected FormatsUtilGeneric formatsUtil;

  @Autowired protected TxUtil txUtil;

  @Autowired protected TaskExecutor taskExecutor;

  @Autowired protected FeeValidationService feeValidationService;

  @Autowired protected CacheService cacheService;

  @Autowired protected CryptoTestUtil cryptoTestUtil;

  @Autowired protected HD_WalletFactoryJava hdWalletFactory;

  @Autowired protected BlameService blameService;

  protected MessageSignUtilGeneric messageSignUtil = MessageSignUtilGeneric.getInstance();

  protected MixLimitsService mixLimitsService;

  private AssertMultiClientManager multiClientManager;

  protected NetworkParameters params;

  protected CliConfig cliConfig;

  @Before
  public void setUp() throws Exception {
    // enable debug
    Utils.setLoggerDebug();

    serverConfig.validate();

    Assert.assertTrue(MockRpcClientServiceImpl.class.isAssignableFrom(rpcClientService.getClass()));
    this.params = cryptoService.getNetworkParameters();

    cliConfig = new CliConfig();

    messageSignUtil = MessageSignUtilGeneric.getInstance();

    dbService.__reset();
    mixLimitsService = mixService.__getMixLimitsService();
    rpcClientService.resetMock();

    configurePools(serverConfig.getMinerFees(), serverConfig.getPools());
    cacheService._reset();
  }

  protected void configurePools(
      WhirlpoolServerConfig.MinerFeeConfig minerFeeConfig,
      WhirlpoolServerConfig.PoolConfig... poolConfigs) {
    poolService.__reset(poolConfigs, minerFeeConfig);
    mixService.__reset();
  }

  protected Mix __nextMix(
      WhirlpoolServerConfig.MinerFeeConfig minerFeeConfig,
      WhirlpoolServerConfig.PoolConfig poolConfig)
      throws IllegalInputException {
    configurePools(minerFeeConfig, poolConfig);
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
      int mustMixMin,
      int liquidityMin,
      int anonymitySet)
      throws IllegalInputException {
    // create new pool
    WhirlpoolServerConfig.PoolConfig poolConfig = new WhirlpoolServerConfig.PoolConfig();
    poolConfig.setId(Utils.generateUniqueString());
    poolConfig.setFeeValue(feeValue);
    poolConfig.setDenomination(denomination);
    poolConfig.setMustMixMin(mustMixMin);
    poolConfig.setLiquidityMin(liquidityMin);
    poolConfig.setAnonymitySet(anonymitySet);

    WhirlpoolServerConfig.MinerFeeConfig minerFeeConfig =
        new WhirlpoolServerConfig.MinerFeeConfig();
    minerFeeConfig.setMinerFeeMin(minerFeeMin);
    minerFeeConfig.setMinerFeeCap(minerFeeCap);
    minerFeeConfig.setMinerFeeMax(minerFeeMax);

    // run new mix for the pool
    return __nextMix(minerFeeConfig, poolConfig);
  }

  protected Mix __nextMix(
      int mustMixMin,
      int liquidityMin,
      int anonymitySet,
      Pool copyPool,
      WhirlpoolServerConfig.MinerFeeConfig minerFeeConfig)
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
    return __nextMix(minerFeeConfig, poolConfig);
  }

  protected Mix __nextMix(int mustMixMin, int liquidityMin, int anonymitySet, Pool copyPool)
      throws IllegalInputException {
    return __nextMix(
        mustMixMin, liquidityMin, anonymitySet, copyPool, copyPool._getMinerFeeConfig());
  }

  protected Mix __getCurrentMix() {
    Pool pool = poolService.getPools().iterator().next();
    return pool.getCurrentMix();
  }

  @AfterAll
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
            port);
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

    TxOutPoint txOutPoint = blockchainDataService.getOutPoint(rpcTransaction, utxoIndex);
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
}
