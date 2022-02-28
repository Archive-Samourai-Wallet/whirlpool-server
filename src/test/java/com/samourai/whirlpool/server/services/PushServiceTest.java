package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.wallet.api.backend.beans.BackendPushTxException;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class PushServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final long FEES_VALID = 975000;

  private static final String SCODE_FOO_0 = "foo";
  private static final short SCODE_FOO_PAYLOAD = 1234;
  private static final String SCODE_BAR_25 = "bar";
  private static final short SCODE_BAR_PAYLOAD = 5678;
  private static final String SCODE_MIN_50 = "min";
  private static final short SCODE_MIN_PAYLOAD = -32768;
  private static final String SCODE_MAX_80 = "maX";
  private static final short SCODE_MAX_PAYLOAD = 32767;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    // scodes
    setScodeConfig(SCODE_FOO_0, SCODE_FOO_PAYLOAD, 0, null);
    setScodeConfig(SCODE_BAR_25, SCODE_BAR_PAYLOAD, 25, null);
    setScodeConfig(SCODE_MIN_50, SCODE_MIN_PAYLOAD, 50, null);
    setScodeConfig(SCODE_MAX_80, SCODE_MAX_PAYLOAD, 80, null);
  }

  @Test
  public void pushTx0_feePayloadValid_0() throws Exception {
    Pool pool = poolService.getPool("0.01btc");
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    pool._setPoolFee(poolFee);

    Transaction tx =
        txUtil.fromTxHex(
            params,
            "0100000000010159f41e245a379baa06661e513508e65b31b7a6b2815258d2c772775068f739cf0000000000ffffffff070000000000000000426a4092c02c0b671d2f725bfe50fe6c777ed1461e535f06865b833daeb861e9166e1e26bcc1a86eaa2f5fbe064cad8e2ece5f3ea1c7721d6134e13a3a3b9a57cb97f95704000000000000160014df3a4bc83635917ad18621f3ba78cef6469c5f59a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a7143100cf8fa90500000000160014a2fc114723a7924b0b056567b5c24d16ce89336902483045022100e7694e7ee44d404da7f0ff76a45d8f60b9ca6a13a013b61b0f22b4d735e9a79402202647897ff6e6fa8f38ac808ef3de19788eaf27e57dd2f145ec3f0c22a0447ed5012102f231dd2f7eff90fe1a770ba585b52d33f6814f883398e4427de3f73203b5d8e300000000");

    try {
      pushService.validateAndPushTx0(tx.bitcoinSerialize(), System.currentTimeMillis(), pool);
      Assertions.assertTrue(false);
    } catch (BackendPushTxException e) {
      // ok
      Assertions.assertEquals("bad-txns-inputs-missingorspent", e.getMessage());
      Assertions.assertEquals("bad-txns-inputs-missingorspent", e.getPushTxError());
    }
  }

  @Test
  public void validate_feePayloadInvalid() throws Exception {
    Pool pool = poolService.getPool("0.01btc");
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    pool._setPoolFee(poolFee);

    Transaction tx =
        txUtil.fromTxHex(
            params,
            "010000000001018a7540589b824a717eb78f0ee4058cc648a6fa955cba89cf313dfa5b812f84920000000000ffffffff070000000000000000426a40835da5acaa9b400d6edcbcbdef56d862aa3047086b7ff65944b20084a184d570455f25f8e9b6f0ad423991c608bfcf79a1f0fa391c6e159310b5aad594ee78f098e00e0000000000160014df3a4bc83635917ad18621f3ba78cef6469c5f59a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014a2fc114723a7924b0b056567b5c24d16ce89336902483045022100e148fcce424ceecfe6eaa29a11cb3a37f303cd4e3bc7504d0dee80b67a575236022000d12ba3d4c72ecb2475ec6ebe48cb5e890121297646c51b2e9bac41c48da60e0121036af5f708b07937984e9657ee375cf70fdca5adbc69af0fbffd98ff7c7385369e00000000");

    try {
      pushService.validateAndPushTx0(tx.bitcoinSerialize(), System.currentTimeMillis(), pool);
      Assertions.assertTrue(false);
    } catch (BackendPushTxException e) {
      // ok
      Assertions.assertEquals("Not a valid TX0", e.getMessage());
      Assertions.assertEquals("Not a valid TX0", e.getPushTxError());
    }
  }

  @Test
  public void validate_feePayload_0_addressReuse() throws Exception {
    Pool pool = poolService.getPool("0.01btc");
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    pool._setPoolFee(poolFee);

    short scodePayload = (short) 12345;
    setScodeConfig("myscode", scodePayload, 0, null);

    Transaction tx =
        txUtil.fromTxHex(
            params,
            "01000000000101bc1e89ab6e49bc256aa9e354e5660cf5c3d1e53267370e27b6716719cf0aac900300000000ffffffff040000000000000000426a401e5947a9e7a284a388ae0c3dd2be0ee194ed056c1b86c1455c774f56419df82d5f8f6154c2f62c328a6c72c55e721eedf16b7df3f319a305c842c537563a49691027000000000000160014d0327e453f042573ed880ddeca5b7fc2c241faa0a6420f00000000001600148c6914eba99128edfaf19709f26183c74ee9bc86e87a9a0000000000160014b9e77b6e1190be4460fccda5dfd1b2cdaa1b1557024730440220662a242f8a2c6a7c810f8b5c2eb46e63c50488f3bdc82a5493490a35da3e5a240220527243aca82f25e2a24b1985027dd002c7732c2a63e93d4ddd234e0811b0b5980121025fb5f55491ca63611977e2e2b6d70fc4121400a441d8c3ba58b4c96c14d71e0a00000000");

    try {
      pushService.validateAndPushTx0(tx.bitcoinSerialize(), System.currentTimeMillis(), pool);
      Assertions.assertTrue(false);
    } catch (BackendPushTxException e) {
      // ok
      Assertions.assertEquals("address-reuse", e.getMessage());
      Assertions.assertEquals("address-reuse", e.getPushTxError());
      Assertions.assertArrayEquals(new Integer[] {1, 2, 3}, e.getVoutsAddressReuse().toArray());
    }
  }

  @Test
  public void pushTx0_feePayloadValid_noScode() throws Exception {
    Pool pool = poolService.getPool("0.01btc");
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    pool._setPoolFee(poolFee);

    Transaction tx =
        txUtil.fromTxHex(
            params,
            "010000000001011c0c981a33079a5f0e71445de7c0b5f776afc03cfdcdc9594465f7c047cd8b6f0000000000ffffffff070000000000000000426a40345bc3d1bd80d66f0f9b2683bba9b2aab388d175ec707d1689d530fcc138ad783882fa661e85607647067e0b112f6690a4505db2947339f100c00045694e43d798e00e0000000000160014c7723363f8df0bffe3dc45f54be7604687de8ab0a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014df3a4bc83635917ad18621f3ba78cef6469c5f590248304502210091724bed32f81fd45fc76166c203d2a7b31cce45117c0b6041c65aa39adc22be022007776fb85d237d1468401ccf50f8c1171289e4960b4a512920f206dcb0b37cfb012103858577e052ab063489c145bd785089dd01f46df220cc858e8b058d140f185ad000000000");

    try {
      pushService.validateAndPushTx0(tx.bitcoinSerialize(), System.currentTimeMillis(), pool);
      Assertions.assertTrue(false);
    } catch (BackendPushTxException e) {
      // ok
      Assertions.assertEquals("bad-txns-inputs-missingorspent", e.getMessage());
      Assertions.assertEquals("bad-txns-inputs-missingorspent", e.getPushTxError());
    }
  }

  @Test
  public void validate_noScode_invalidAddress() throws Exception {
    Pool pool = poolService.getPool("0.01btc");
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    pool._setPoolFee(poolFee);

    Transaction tx =
        txUtil.fromTxHex(
            params,
            "01000000000101832108846e099fda3cc114f71067541d07b31c137b2e7efef788f981e9ea5bc10000000000ffffffff070000000000000000426a40236cb049e7dab3370c093f9a2864177a7fa1c6e63b5aa7369096c46fcb1f5af7d5b2e8a0305195c0dafedba10e6270fee3d90b5a54afb4554962e6b5b36a868e98e00e00000000001600142a64f8ea17ebf6c5501bd0f96f7cf43114e26801a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014df3a4bc83635917ad18621f3ba78cef6469c5f590247304402201e65a492f05f8e2c92bd29ea43b94f3f52d4971a2e9dae3ef65b0946098c00c002200851df8d58e1739ebec41260bd5716b2593e5336605c76708106e98787c1ffbe0121031d660b9b9912a9bbab5db732d0240468c2fc67aa2a80a97b69efba126fd5322a00000000");

    try {
      pushService.validateAndPushTx0(tx.bitcoinSerialize(), System.currentTimeMillis(), pool);
      Assertions.assertTrue(false);
    } catch (BackendPushTxException e) {
      // ok
      Assertions.assertEquals("Not a valid TX0", e.getMessage());
      Assertions.assertEquals("Not a valid TX0", e.getPushTxError());
    }
  }
  /*
  @Test
  public void validate_feePayloadInvalid() throws Exception {
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
    byte[] feePayload = feePayloadService.encodeFeePayload(feeIndex, scodePayload, partnerPayload);
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
        new Tx0Service(whirlpoolWalletConfig, tx0PreviewService)
            .tx0(
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

    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool(pool.getPoolId());
    serverPool._setPoolFee(poolFee);

    Tx0Validation tx0Validation = tx0ValidationService.validate(tx0.getTx(), 1234, poolFee);
    Assertions.assertNull(tx0Validation);

    // reverse parseAndValidate
    try {
      Integer[] strictModeVouts = new Integer[] {};
      doParseAndValidate(tx0Validation, tx0.getTx(), serverPool, strictModeVouts);
      Assertions.assertTrue(false);
    } catch (NotifiableException e) {
      Assertions.assertEquals("Not a valid TX0", e.getMessage());
    }
  }

  @Test
  public void validate_feePayload_0() throws Exception {
    // reject nofee when unknown feePayload
    String txid = "b3557587f87bcbd37e847a0fff0ded013b23026f153d85f28cb5d407d39ef2f3";
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    Transaction tx = getTx(txid);
    Tx0Validation tx0Validation = tx0ValidationService.validate(tx, 1234, poolFee);
    Assertions.assertNull(tx0Validation);

    // reverse parseAndValidate
    try {
      Integer[] strictModeVouts = new Integer[] {};
      doParseAndValidate(tx0Validation, tx, serverPool, strictModeVouts);
      Assertions.assertTrue(false);
    } catch (NotifiableException e) {
      Assertions.assertEquals("Not a valid TX0", e.getMessage());
    }

    // accept when valid feePayload
    short scodePayload = (short) 12345;
    short partnerPayload = 0;
    setScodeConfig("myscode", scodePayload, 0, null);
    tx0Validation = tx0ValidationService.validate(tx, 1234, poolFee);
    Assertions.assertNull(tx0Validation.getFeeOutput());
    Assertions.assertEquals(scodePayload, tx0Validation.getScodeConfig().getPayload());

    WhirlpoolFeeData feeData = tx0Validation.getFeeData();
    Assertions.assertEquals(11, feeData.getFeeIndice());
    Assertions.assertEquals(scodePayload, feeData.getScodePayload());
    Assertions.assertEquals(partnerPayload, feeData.getPartnerPayload());

    // reverse parseAndValidate
    Integer[] strictModeVouts = new Integer[] {1, 2, 3};
    doParseAndValidate(tx0Validation, tx, serverPool, strictModeVouts);
  }

  @Test
  public void validate_noScode() throws Exception {
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
        feePayloadService.encodeFeePayload(feeIndex, scodePayload, (short) 0); // no scodePayload
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
        new Tx0Service(whirlpoolWalletConfig, tx0PreviewService)
            .tx0(
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

    // reverse parseAndValidate
    Integer[] strictModeVouts = new Integer[] {2, 3, 4, 5, 6};
    doParseAndValidate(tx0Validation, tx0.getTx(), serverPool, strictModeVouts);
  }

  @Test
  public void validate_noScode_invalidAddress() throws Exception {
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
        feePayloadService.encodeFeePayload(feeIndex, scodePayload, (short) 0); // no scodePayload
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
        new Tx0Service(whirlpoolWalletConfig, tx0PreviewService)
            .tx0(
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

    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool(pool.getPoolId());
    serverPool._setPoolFee(poolFee);

    Tx0Validation tx0Validation = tx0ValidationService.validate(tx0.getTx(), 1234, poolFee);
    Assertions.assertNull(tx0Validation);

    // reverse parseAndValidate
    try {
      Integer[] strictModeVouts = new Integer[] {};
      doParseAndValidate(tx0Validation, tx0.getTx(), serverPool, strictModeVouts);
      Assertions.assertTrue(false);
    } catch (NotifiableException e) {
      Assertions.assertEquals("Not a valid TX0", e.getMessage());
    }
  }*/
}
