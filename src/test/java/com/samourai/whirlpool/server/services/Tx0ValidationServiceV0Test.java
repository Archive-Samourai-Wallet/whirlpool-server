package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.client.tx0.Tx0PreviewService;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.data.minerFee.BasicMinerFeeSupplier;
import com.samourai.whirlpool.protocol.feeOpReturn.FeeOpReturnImplV0;
import com.samourai.whirlpool.protocol.feeOpReturn.FeeOpReturnImplV1;
import com.samourai.whirlpool.protocol.feePayload.FeePayloadV1;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.beans.Tx0Validation;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.services.fee.WhirlpoolFeeData;
import com.samourai.xmanager.protocol.XManagerService;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class Tx0ValidationServiceV0Test extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  protected static final long FEES_VALID = 975000;
  protected static final long FEES_VALID_50K = 50000;

  protected static final String SCODE_FOO_0 = "foo";
  protected static final short SCODE_FOO_PAYLOAD = 1234;
  protected static final String SCODE_BAR_25 = "bar";
  protected static final short SCODE_BAR_PAYLOAD = 5678;
  protected static final String SCODE_MIN_50 = "min";
  protected static final short SCODE_MIN_PAYLOAD = -32768;
  protected static final String SCODE_MAX_80 = "maX";
  protected static final short SCODE_MAX_PAYLOAD = 32767;

  private WhirlpoolWalletConfig whirlpoolWalletConfig;
  private Tx0PreviewService tx0PreviewService;

  @Autowired private FeeOpReturnImplV0 feeOpReturnImplV0;
  @Autowired private FeeOpReturnImplV1 feeOpReturnImplV1;
  protected Tx0Service tx0Service;

  protected void setupFeeOpReturnImpl() {
    // use FeeOpReturnImplV0
    feePayloadService._setFeeOpReturnImplCurrent(feeOpReturnImplV0);
    Assertions.assertEquals(
        FeeOpReturnImplV0.OP_RETURN_VERSION,
        feePayloadService._getFeeOpReturnImplCurrent().getOpReturnVersion());
  }

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    dbService.__reset();

    setupFeeOpReturnImpl();

    // scodes
    setScodeConfig(SCODE_FOO_0, SCODE_FOO_PAYLOAD, 0, null);
    setScodeConfig(SCODE_BAR_25, SCODE_BAR_PAYLOAD, 25, null);
    setScodeConfig(SCODE_MIN_50, SCODE_MIN_PAYLOAD, 50, null);
    setScodeConfig(SCODE_MAX_80, SCODE_MAX_PAYLOAD, 80, null);

    whirlpoolWalletConfig = computeWhirlpoolWalletConfig();
    tx0PreviewService =
        new Tx0PreviewService(new BasicMinerFeeSupplier(1, 9999, 1), whirlpoolWalletConfig);

    tx0Service =
        new Tx0Service(
            poolService,
            partnerService,
            tx0ValidationService,
            scodeService,
            feePayloadService,
            exportService,
            serverConfig,
            serverContext,
            xManagerClient,
            backendService,
            metricService);
  }

  private void assertFeeData(String txid, int feeIndice, short scodePayload) throws Exception {
    RpcTransaction rpcTransaction =
        blockchainDataService
            .getRpcTransaction(txid)
            .orElseThrow(() -> new NoSuchElementException());
    WhirlpoolFeeData feeData = tx0ValidationService.decodeFeeData(rpcTransaction.getTx());
    Assertions.assertEquals(feeIndice, feeData.getFeeIndice());
    Assertions.assertEquals(scodePayload, feeData.getScodePayload());
  }

  private void assertFeeDataError(String txid, String errorMessage) {
    RpcTransaction rpcTransaction =
        blockchainDataService
            .getRpcTransaction(txid)
            .orElseThrow(() -> new NoSuchElementException());

    Exception e =
        Assertions.assertThrows(
            Exception.class, () -> tx0ValidationService.decodeFeeData(rpcTransaction.getTx()));
    Assertions.assertEquals(errorMessage, e.getMessage());
  }

  @Test
  public void findFeeDataV0() throws Exception {
    // invalid tx0
    assertFeeDataError(
        "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", "feeOutput not found");

    // not a tx0
    assertFeeDataError(
        "7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16", "feeOutput not found");

    // not a tx0
    assertFeeDataError(
        "5369dfb71b36ed2b91ca43f388b869e617558165e4f8306b80857d88bdd624f2", "feeOutput not found");

    // valid tx0
    assertFeeData("6588946af1d9d92b402fd672360fd12217abfaf6382ce644d358e8174781f0ce", 0, (short) 0);
    assertFeeData(
        "b3557587f87bcbd37e847a0fff0ded013b23026f153d85f28cb5d407d39ef2f3", 11, (short) 12345);
    assertFeeData(
        "aa77a502ca48540706c6f4a62f6c7155ee415c344a4481e0bf945fb56bbbdfdd", 12, (short) 0);
    assertFeeData(
        "604dac3fa5f83b810fc8f4e8d94d9283e4d0b53e3831d0fe6dc9ecdb15dd8dfb", 13, (short) 0);
  }

  @Test
  public void findValidFeeOutput_feeValueV0() throws Exception {
    String txid = "6588946af1d9d92b402fd672360fd12217abfaf6382ce644d358e8174781f0ce";

    // accept when paid exact fee
    Assertions.assertEquals(
        1, doFindValidFeeOutput(txid, 1234, FEES_VALID_50K, 0, null, 100).getIndex());

    // reject when paid more than fee
    Exception e =
        Assertions.assertThrows(
            Exception.class,
            () -> doFindValidFeeOutput(txid, 1234, FEES_VALID_50K - 1, 0, null, 100));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());

    e =
        Assertions.assertThrows(
            Exception.class, () -> doFindValidFeeOutput(txid, 1234, 1, 0, null, 100));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());

    // reject when paid less than fee
    e =
        Assertions.assertThrows(
            Exception.class,
            () -> doFindValidFeeOutput(txid, 1234, FEES_VALID_50K + 1, 0, null, 100));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());

    e =
        Assertions.assertThrows(
            Exception.class, () -> doFindValidFeeOutput(txid, 1234, 1000000, 0, null, 100));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());

    // reject when paid to wrong xpub indice
    e =
        Assertions.assertThrows(
            Exception.class, () -> doFindValidFeeOutput(txid, 234, FEES_VALID_50K, 1, null, 100));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());

    e =
        Assertions.assertThrows(
            Exception.class, () -> doFindValidFeeOutput(txid, 234, FEES_VALID_50K, 2, null, 100));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());

    e =
        Assertions.assertThrows(
            Exception.class, () -> doFindValidFeeOutput(txid, 234, FEES_VALID_50K, 10, null, 100));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());
  }

  @Test
  public void findValidFeeOutput_feeAcceptV0() throws Exception {
    String txid = "6588946af1d9d92b402fd672360fd12217abfaf6382ce644d358e8174781f0ce";
    Map<Long, Long> feeAccept = new HashMap<>();
    feeAccept.put(FEES_VALID_50K, 11111111L);

    // reject when no feeAccept
    Exception e =
        Assertions.assertThrows(
            Exception.class,
            () -> doFindValidFeeOutput(txid, 1234, FEES_VALID_50K + 10, 0, null, 100));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());

    // accept when tx0Time <= feeAccept.maxTime
    Assertions.assertEquals(
        1,
        doFindValidFeeOutput(txid, 11111110L, FEES_VALID_50K + 10, 0, feeAccept, 100).getIndex());
    Assertions.assertEquals(
        1, doFindValidFeeOutput(txid, 11110L, FEES_VALID_50K + 10, 0, feeAccept, 100).getIndex());

    // reject when tx0Time > feeAccept.maxTime
    e =
        Assertions.assertThrows(
            Exception.class,
            () -> doFindValidFeeOutput(txid, 11111112L, FEES_VALID_50K + 10, 0, feeAccept, 100));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());
  }

  private TransactionOutput doFindValidFeeOutput(
      String txid,
      long txTime,
      long minFees,
      int xpubIndice,
      Map<Long, Long> feeAccept,
      int feeValuePercent)
      throws Exception {
    PoolFee poolFee = new PoolFee(minFees, feeAccept);
    return tx0ValidationService.findValidFeeOutput(
        getTx(txid), txTime, xpubIndice, poolFee, feeValuePercent, XManagerService.WHIRLPOOL);
  }

  private Transaction getTx(String txid) {
    RpcTransaction rpcTransaction =
        blockchainDataService
            .getRpcTransaction(txid)
            .orElseThrow(() -> new NoSuchElementException());
    return rpcTransaction.getTx();
  }

  /*@Test
  public void generate_feePayloadValid() throws Exception {
    UnspentOutput spendFrom =
        testUtils.generateUnspentOutputWithKey(99000000, params, utxoKeyProvider);
    Collection<UnspentOutput> spendFroms = Arrays.asList(spendFrom);
    HD_Wallet bip84w =
        hdWalletFactory.restoreWallet(
            "all all all all all all all all all all all all", "test", params);

    BipWallet depositWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.DEPOSIT_BIP84);
    BipWallet premixWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.PREMIX_BIP84);
    BipWallet postmixWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.POSTMIX_BIP84);
    BipWallet badBankWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.BADBANK_BIP84);

    Pool pool = new Pool();
    pool.setPoolId("0.01btc");
    pool.setDenomination(1000000);
    pool.setFeeValue(FEES_VALID);
    pool.setMustMixBalanceMin(1000102);
    pool.setMustMixBalanceMax(1010000);
    pool.setMinAnonymitySet(1);
    pool.setMixAnonymitySet(2);
    pool.setMinMustMix(1);
    pool.setTx0MaxOutputs(70);

    int feeIndex = 123456;
    short scodePayload = SCODE_FOO_PAYLOAD; // valid scodePayload
    short partnerPayload = 0;
    String feePaymentCode = tx0ValidationService.getFeePaymentCode();
    String feeAddress = "tb1q9fj036sha0mv25qm6ruk7l85xy2wy6qp853yx0";
    byte[] feePayload = feePayloadService.computeFeePayload(feeIndex, scodePayload, partnerPayload);

    Tx0Data tx0Data =
        new Tx0Data("0.01btc", feePaymentCode, 0, 1111, 100, "test", feePayload, feeAddress);

    int nbPremix = 4;
    long tx0MinerFee = 2;
    long premixMinerFee = 102;
    long mixMinerFee = premixMinerFee * nbPremix;
    long premixValue = 1000102;
    long changeValue = 94998479;
    Tx0Preview tx0Preview =
        new Tx0Preview(
            pool,
            tx0Data,
            123,
            tx0MinerFee,
            premixMinerFee,
            mixMinerFee,
            1,
            1,
            premixValue,
            changeValue,
            nbPremix);
    Tx0 tx0 =
        tx0Service.tx0(
            spendFroms,
            depositWallet,
            premixWallet,
            postmixWallet,
            badBankWallet,
            new Tx0Config(
                tx0PreviewService,
                Arrays.asList(pool),
                Tx0FeeTarget.BLOCKS_2,
                Tx0FeeTarget.BLOCKS_2,
                WhirlpoolAccount.DEPOSIT),
            tx0Preview,
            utxoKeyProvider);

    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool(pool.getPoolId());
    serverPool._setPoolFee(poolFee);

    Tx0Validation tx0Validation = tx0ValidationService.validate(tx0.getTx(), 1234, poolFee);
    Assertions.assertNull(tx0Validation.getFeeOutput());
    Assertions.assertEquals(scodePayload, tx0Validation.getScodeConfig().getPayload());

    WhirlpoolFeeData feeData = tx0Validation.getFeeData();
    Assertions.assertEquals(feeIndex, feeData.getFeeIndice());
    Assertions.assertEquals(scodePayload, feeData.getScodePayload());
    Assertions.assertEquals(partnerPayload, feeData.getPartnerPayload());
    Assertions.assertEquals(FeePayloadV1.FEE_PAYLOAD_VERSION, feeData.getFeePayloadVersion());
    Assertions.assertEquals(feePayloadService._getFeeOpReturnImplCurrent().getOpReturnVersion(), feeData.getOpReturnVersion());

    // reverse parseAndValidate
    Integer[] strictModeVouts = new Integer[] {1, 2, 3, 4, 5, 6};
    doParseAndValidate(tx0Validation, tx0.getTx(), serverPool, strictModeVouts);
  }*/

  @Test
  public void validate_raw_feePayloadValid_V0() throws Exception {
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    String txHex =
        "01000000000101465d3f63c2ed1735eb975deb6043a82d3a72215dcb07a04a3632474e0b927a290000000000ffffffff070000000000000000426a40c58ee04517568428085179290f94ef292985e8b44c252467b3857f54b16baec994e77c76e3886c57816c76bda04975d1a37570dc02940f9209388c46aae5dbed5704000000000000160014df3a4bc83635917ad18621f3ba78cef6469c5f59a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a7143100cf8fa90500000000160014a2fc114723a7924b0b056567b5c24d16ce89336902483045022100f6b62eafbfedbe09f467f29bd44bdb3b22fe214049b2527f3faddfb6565c996402205692112995d93a61940863c33def4bc105c2f830a126ce6184720e91440995ca0121023ad54a5242de8cf7780471f4416a79a34b2bc0955ca1889510bb42e80daf0dd500000000";
    Transaction tx = txUtil.fromTxHex(params, txHex);

    int feeIndex = 123456;
    short scodePayload = SCODE_FOO_PAYLOAD; // valid scodePayload
    short partnerPayload = 0;

    Tx0Validation tx0Validation = tx0ValidationService.validate(tx, 1234, poolFee);
    Assertions.assertNull(tx0Validation.getFeeOutput());
    Assertions.assertEquals(scodePayload, tx0Validation.getScodeConfig().getPayload());

    WhirlpoolFeeData feeData = tx0Validation.getFeeData();
    Assertions.assertEquals(feeIndex, feeData.getFeeIndice());
    Assertions.assertEquals(scodePayload, feeData.getScodePayload());
    Assertions.assertEquals(partnerPayload, feeData.getPartnerPayload());
    Assertions.assertEquals(FeePayloadV1.FEE_PAYLOAD_VERSION, feeData.getFeePayloadVersion());
    Assertions.assertEquals(FeeOpReturnImplV0.OP_RETURN_VERSION, feeData.getOpReturnVersion());

    // reverse parseAndValidate
    Integer[] strictModeVouts = new Integer[] {1, 2, 3, 4, 5, 6};
    doParseAndValidate(tx0Validation, tx, serverPool, strictModeVouts);
  }

  /*@Test
  public void generate_feePayloadInvalidV0() throws Exception {
    UnspentOutput spendFrom =
        testUtils.generateUnspentOutputWithKey(99000000, params, utxoKeyProvider);
    Collection<UnspentOutput> spendFroms = Arrays.asList(spendFrom);
    HD_Wallet bip84w =
        hdWalletFactory.restoreWallet(
            "all all all all all all all all all all all all", "test", params);

    BipWallet depositWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.DEPOSIT_BIP84);
    BipWallet premixWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.PREMIX_BIP84);
    BipWallet postmixWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.POSTMIX_BIP84);
    BipWallet badBankWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.BADBANK_BIP84);

    Pool pool = new Pool();
    pool.setPoolId("0.01btc");
    pool.setDenomination(1000000);
    pool.setFeeValue(FEES_VALID);
    pool.setMustMixBalanceMin(1000102);
    pool.setMustMixBalanceMax(1010000);
    pool.setMinAnonymitySet(1);
    pool.setMixAnonymitySet(2);
    pool.setMinMustMix(1);
    pool.setTx0MaxOutputs(70);
    List<Pool> poolItems = new ArrayList<>();
    poolItems.add(pool);

    String feePaymentCode = tx0ValidationService.getFeePaymentCode();
    int feeIndex = 123456;
    short scodePayload = 111; // invalid scodePayload
    short partnerPayload = 0;
    byte[] feePayload = feePayloadService.computeFeePayload(feeIndex, scodePayload, partnerPayload);
    String feeAddress = "tb1q9fj036sha0mv25qm6ruk7l85xy2wy6qp853yx0";

    Tx0Data tx0Data =
        new Tx0Data("0.01btc", feePaymentCode, 0, FEES_VALID, 0, "test", feePayload, feeAddress);

    int nbPremix = 4;
    long tx0MinerFee = 2;
    long premixMinerFee = 102;
    long mixMinerFee = premixMinerFee * nbPremix;
    long premixValue = 1000102;
    long changeValue = 94024590;
    Tx0Preview tx0Preview =
        new Tx0Preview(
            pool,
            tx0Data,
            123,
            tx0MinerFee,
            premixMinerFee,
            mixMinerFee,
            1,
            1,
            premixValue,
            changeValue,
            nbPremix);

    Tx0 tx0 =
        tx0Service.tx0(
            spendFroms,
            depositWallet,
            premixWallet,
            postmixWallet,
            badBankWallet,
            new Tx0Config(
                tx0PreviewService,
                Arrays.asList(pool),
                Tx0FeeTarget.BLOCKS_2,
                Tx0FeeTarget.BLOCKS_2,
                WhirlpoolAccount.DEPOSIT),
            tx0Preview,
            utxoKeyProvider);

    WhirlpoolFeeData feeData = tx0ValidationService.decodeFeeData(tx0.getTx());
    Assertions.assertEquals(feeIndex, feeData.getFeeIndice());
    Assertions.assertEquals(scodePayload, feeData.getScodePayload());
    Assertions.assertEquals(partnerPayload, feeData.getPartnerPayload());
    Assertions.assertEquals(FeePayloadV1.FEE_PAYLOAD_VERSION, feeData.getFeePayloadVersion());
    Assertions.assertEquals(feePayloadService._getFeeOpReturnImplCurrent().getOpReturnVersion(), feeData.getOpReturnVersion());

    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool(pool.getPoolId());
    serverPool._setPoolFee(poolFee);

    Assertions.assertThrows(
        Exception.class,
        () -> tx0ValidationService.validate(tx0.getTx(), 1234, poolFee),
        "Not a valid TX0");
  }*/

  @Test
  public void validate_raw_feePayloadInvalidV0() throws Exception {
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    String txHex =
        "010000000001015cfb347c26ab871701abd6cd2ba520fd1cd2fdea02b0ade99819b0b18395e2a10000000000ffffffff070000000000000000426a40188ec2c5ee1080ea32e0a0238df851586eb91102c073b0ffa9c0c97cd451d969e8bf3068cf83f64af2d9c935adce0bae5dd20ea8575d1d9d60d565518376db4798e00e0000000000160014df3a4bc83635917ad18621f3ba78cef6469c5f59a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014a2fc114723a7924b0b056567b5c24d16ce8933690247304402206c46aa627c636a4fdbefc7a16214be37ec90a0654bdf509dc20afa6393d78cd402205c2f0ddb1167452cd977bbb1fe7658d5683767c7f3634452d1ff090de662e4970121039b645a6ef7a274ca0cde38f78524c191525defd545d196b48a6e0594611d0b6c00000000";
    Transaction tx = txUtil.fromTxHex(params, txHex);
    Exception e =
        Assertions.assertThrows(
            Exception.class, () -> tx0ValidationService.validate(tx, 1234, poolFee));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());
  }

  @Test
  public void validate_txid_feePayload_0_addressReuseV0() throws Exception {
    // reject nofee when unknown feePayload
    String txid = "b3557587f87bcbd37e847a0fff0ded013b23026f153d85f28cb5d407d39ef2f3";
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    Transaction tx = getTx(txid);
    Exception e =
        Assertions.assertThrows(
            Exception.class, () -> tx0ValidationService.validate(tx, 1234, poolFee));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());

    // accept when valid feePayload
    short scodePayload = (short) 12345;
    short partnerPayload = 0;
    setScodeConfig("myscode", scodePayload, 0, null);
    Tx0Validation tx0Validation = tx0ValidationService.validate(tx, 1234, poolFee);
    Assertions.assertNull(tx0Validation.getFeeOutput());
    Assertions.assertEquals(scodePayload, tx0Validation.getScodeConfig().getPayload());

    WhirlpoolFeeData feeData = tx0Validation.getFeeData();
    Assertions.assertEquals(11, feeData.getFeeIndice());
    Assertions.assertEquals(scodePayload, feeData.getScodePayload());
    Assertions.assertEquals(partnerPayload, feeData.getPartnerPayload());
    Assertions.assertEquals(FeePayloadV1.FEE_PAYLOAD_VERSION, feeData.getFeePayloadVersion());
    Assertions.assertEquals(FeeOpReturnImplV0.OP_RETURN_VERSION, feeData.getOpReturnVersion());

    // reverse parseAndValidate
    Integer[] strictModeVouts = new Integer[] {1, 2, 3};
    doParseAndValidate(tx0Validation, tx, serverPool, strictModeVouts);
  }

  /*@Test
  public void generate_noScode() throws Exception {
    UnspentOutput spendFrom =
        testUtils.generateUnspentOutputWithKey(99000000, params, utxoKeyProvider);
    Collection<UnspentOutput> spendFroms = Arrays.asList(spendFrom);
    HD_Wallet bip84w =
        hdWalletFactory.restoreWallet(
            "all all all all all all all all all all all all", "test", params);

    BipWallet depositWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.DEPOSIT_BIP84);
    BipWallet premixWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.PREMIX_BIP84);
    BipWallet postmixWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.POSTMIX_BIP84);
    BipWallet badBankWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.BADBANK_BIP84);

    Pool pool = new Pool();
    pool.setPoolId("0.01btc");
    pool.setDenomination(1000000);
    pool.setFeeValue(FEES_VALID);
    pool.setMustMixBalanceMin(1000102);
    pool.setMustMixBalanceMax(1010000);
    pool.setMinAnonymitySet(1);
    pool.setMixAnonymitySet(2);
    pool.setMinMustMix(1);
    pool.setTx0MaxOutputs(70);
    List<Pool> poolItems = new ArrayList<>();
    poolItems.add(pool);

    String feePaymentCode = tx0ValidationService.getFeePaymentCode();
    int feeIndex = 1;
    short scodePayload = 0;
    byte[] feePayload =
        feePayloadService.computeFeePayload(feeIndex, scodePayload, (short) 0); // no scodePayload
    String feeAddress = "tb1qcaerxclcmu9llc7ugh65hemqg6raaz4sul535f";

    Tx0Data tx0Data =
        new Tx0Data("0.01btc", feePaymentCode, FEES_VALID, 0, 0, "test", feePayload, feeAddress);

    int nbPremix = 4;
    long tx0MinerFee = 2;
    long premixMinerFee = 102;
    long mixMinerFee = premixMinerFee * nbPremix;
    long premixValue = 1000102;
    long changeValue = 94024590;
    Tx0Preview tx0Preview =
        new Tx0Preview(
            pool,
            tx0Data,
            123,
            tx0MinerFee,
            premixMinerFee,
            mixMinerFee,
            1,
            1,
            premixValue,
            changeValue,
            nbPremix);

    Tx0 tx0 =
        tx0Service.tx0(
            spendFroms,
            depositWallet,
            premixWallet,
            postmixWallet,
            badBankWallet,
            new Tx0Config(
                tx0PreviewService,
                Arrays.asList(pool),
                Tx0FeeTarget.BLOCKS_2,
                Tx0FeeTarget.BLOCKS_2,
                WhirlpoolAccount.DEPOSIT),
            tx0Preview,
            utxoKeyProvider);

    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool(pool.getPoolId());
    serverPool._setPoolFee(poolFee);

    Tx0Validation tx0Validation = tx0ValidationService.validate(tx0.getTx(), 1234, poolFee);
    Assertions.assertEquals(1, tx0Validation.getFeeOutput().getIndex());
    Assertions.assertNull(tx0Validation.getScodeConfig());

    WhirlpoolFeeData feeData = tx0Validation.getFeeData();
    Assertions.assertEquals(feeIndex, feeData.getFeeIndice());
    Assertions.assertEquals(scodePayload, feeData.getScodePayload());
    Assertions.assertEquals(FeePayloadV1.FEE_PAYLOAD_VERSION, feeData.getFeePayloadVersion());
    Assertions.assertEquals(feePayloadService._getFeeOpReturnImplCurrent().getOpReturnVersion(), feeData.getOpReturnVersion());

    // reverse parseAndValidate
    Integer[] strictModeVouts = new Integer[] {2, 3, 4, 5, 6};
    doParseAndValidate(tx0Validation, tx0.getTx(), serverPool, strictModeVouts);
  }*/

  @Test
  public void validate_raw_noScodeV0() throws Exception {
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    String txHex =
        "01000000000101563e63135e4537a511f29bc758ca49377ac0001fe231e1c8a73e989aa7193ad20000000000ffffffff070000000000000000426a408d7bde3432995a22af65686012c22e5884a25df168225a292442348a8798b6a1f2fa92a86be676a52a6f5296f5a4c1d2dd0785b52ceb93b736248f46095dbdfc98e00e0000000000160014c7723363f8df0bffe3dc45f54be7604687de8ab0a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014df3a4bc83635917ad18621f3ba78cef6469c5f5902473044022010655cd8abfb61eaeaf9931e45c48062f1a2d50a6a12f4a4377c6fb671d3ce4f02204e21b5ecc29694e6d00e8f03a261fff4892ffcf939ffcde9b1762612eccec803012102b221dc227f0fd4ecf73407529ecea2fecd57bc1016741b0de7a2e3e7d89538cb00000000";
    Transaction tx = txUtil.fromTxHex(params, txHex);

    Tx0Validation tx0Validation = tx0ValidationService.validate(tx, 1234, poolFee);
    Assertions.assertEquals(1, tx0Validation.getFeeOutput().getIndex());
    Assertions.assertNull(tx0Validation.getScodeConfig());

    int feeIndex = 1;
    short scodePayload = 0;
    WhirlpoolFeeData feeData = tx0Validation.getFeeData();
    Assertions.assertEquals(feeIndex, feeData.getFeeIndice());
    Assertions.assertEquals(scodePayload, feeData.getScodePayload());
    Assertions.assertEquals(FeePayloadV1.FEE_PAYLOAD_VERSION, feeData.getFeePayloadVersion());
    Assertions.assertEquals(FeeOpReturnImplV0.OP_RETURN_VERSION, feeData.getOpReturnVersion());

    // reverse parseAndValidate
    Integer[] strictModeVouts = new Integer[] {2, 3, 4, 5, 6};
    doParseAndValidate(tx0Validation, tx, serverPool, strictModeVouts);
  }

  /*@Test
  public void generate_noScode_invalidAddressV0() throws Exception {
    UnspentOutput spendFrom =
        testUtils.generateUnspentOutputWithKey(99000000, params, utxoKeyProvider);
    Collection<UnspentOutput> spendFroms = Arrays.asList(spendFrom);
    HD_Wallet bip84w =
        hdWalletFactory.restoreWallet(
            "all all all all all all all all all all all all", "test", params);

    BipWallet depositWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.DEPOSIT_BIP84);
    BipWallet premixWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.PREMIX_BIP84);
    BipWallet postmixWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.POSTMIX_BIP84);
    BipWallet badBankWallet =
        new BipWallet(bip84w, new MemoryIndexHandlerSupplier(), BIP_WALLET.BADBANK_BIP84);

    Pool pool = new Pool();
    pool.setPoolId("0.01btc");
    pool.setDenomination(1000000);
    pool.setFeeValue(FEES_VALID);
    pool.setMustMixBalanceMin(1000102);
    pool.setMustMixBalanceMax(1010000);
    pool.setMinAnonymitySet(1);
    pool.setMixAnonymitySet(2);
    pool.setMinMustMix(1);
    pool.setTx0MaxOutputs(70);
    List<Pool> poolItems = new ArrayList<>();
    poolItems.add(pool);

    int feeIndex = 123456;
    short scodePayload = 0;
    String feePaymentCode = tx0ValidationService.getFeePaymentCode();
    byte[] feePayload =
        feePayloadService.computeFeePayload(feeIndex, scodePayload, (short) 0); // no scodePayload
    String feeAddress = "tb1q9fj036sha0mv25qm6ruk7l85xy2wy6qp853yx0"; // invalid address

    Tx0Data tx0Data =
        new Tx0Data("0.01btc", feePaymentCode, FEES_VALID, 0, 0, "test", feePayload, feeAddress);

    int nbPremix = 4;
    long tx0MinerFee = 2;
    long premixMinerFee = 102;
    long mixMinerFee = premixMinerFee * nbPremix;
    long premixValue = 1000102;
    long changeValue = 94024590;
    Tx0Preview tx0Preview =
        new Tx0Preview(
            pool,
            tx0Data,
            123,
            tx0MinerFee,
            premixMinerFee,
            mixMinerFee,
            1,
            1,
            premixValue,
            changeValue,
            nbPremix);

    Tx0 tx0 =
        tx0Service.tx0(
            spendFroms,
            depositWallet,
            premixWallet,
            postmixWallet,
            badBankWallet,
            new Tx0Config(
                tx0PreviewService,
                Arrays.asList(pool),
                Tx0FeeTarget.BLOCKS_2,
                Tx0FeeTarget.BLOCKS_2,
                WhirlpoolAccount.DEPOSIT),
            tx0Preview,
            utxoKeyProvider);

    WhirlpoolFeeData feeData = tx0ValidationService.decodeFeeData(tx0.getTx());
    Assertions.assertEquals(feeIndex, feeData.getFeeIndice());
    Assertions.assertEquals(scodePayload, feeData.getScodePayload());
    Assertions.assertEquals(FeePayloadV1.FEE_PAYLOAD_VERSION, feeData.getFeePayloadVersion());
    Assertions.assertEquals(feePayloadService._getFeeOpReturnImplCurrent().getOpReturnVersion(), feeData.getOpReturnVersion());

    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool(pool.getPoolId());
    serverPool._setPoolFee(poolFee);

    Assertions.assertThrows(
        Exception.class,
        () -> tx0ValidationService.validate(tx0.getTx(), 1234, poolFee),
        "Not a valid TX0");
  }*/

  @Test
  public void validate_raw_noScode_invalidAddressV0() throws Exception {
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    String txHex =
        "01000000000101f94906397bf14055ada7430aa55e74b52ee80e95a11b43c487ac497c745a9fb40000000000ffffffff070000000000000000426a4004264aa79e320240ea2a3eab63128916ab724ccfef667bb5954604c41c3558f0d10c6cffb500ff746e8a3b3244f2c1bd2390f9ffab7faa378d14b664336c5d1a98e00e00000000001600142a64f8ea17ebf6c5501bd0f96f7cf43114e26801a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014df3a4bc83635917ad18621f3ba78cef6469c5f590247304402201a24a3bc19d0e9840058385b8d0ece8102fb72d94951b00e5bf99183f2e1220302205a7c609c4a0da54fca3533e15de1b865d6c1b12647569fd827b87abab86b360f012103e05cde9b0cdfdc717e8e3b1d816bfda440dda03cb433d67722549a672f6743c700000000";
    Transaction tx = txUtil.fromTxHex(params, txHex);
    Exception e =
        Assertions.assertThrows(
            Exception.class, () -> tx0ValidationService.validate(tx, 1234, poolFee));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());
  }

  protected void doParseAndValidate(
      Tx0Validation tv1,
      Transaction tx,
      com.samourai.whirlpool.server.beans.Pool serverPool,
      Integer[] strictModeVouts)
      throws Exception {
    Tx0Validation tv2 =
        tx0ValidationService.parseAndValidate(
            tx.bitcoinSerialize(), System.currentTimeMillis(), serverPool);
    if (tv1.getFeeOutput() != null) {
      Assertions.assertEquals(tv1.getFeeOutput().getIndex(), tv2.getFeeOutput().getIndex());
    } else {
      Assertions.assertNull(tv2.getFeeOutput());
    }
    if (tv1.getScodeConfig() != null) {
      Assertions.assertEquals(tv1.getScodeConfig().getPayload(), tv2.getScodeConfig().getPayload());
    } else {
      Assertions.assertNull(tv2.getScodeConfig());
    }
    Assertions.assertEquals(tv1.getTx().getHashAsString(), tv2.getTx().getHashAsString());
    Assertions.assertEquals(tv1.getFeePercent(), tv2.getFeePercent());
    Assertions.assertEquals(tv1.getFeeData().getFeeIndice(), tv2.getFeeData().getFeeIndice());
    Assertions.assertEquals(tv1.getFeeData().getScodePayload(), tv2.getFeeData().getScodePayload());
    Assertions.assertEquals(
        tv1.getFeeData().getPartnerPayload(), tv2.getFeeData().getPartnerPayload());

    Assertions.assertArrayEquals(
        strictModeVouts, tv2.findStrictModeVouts().toArray(new Integer[] {}));
  }
}
