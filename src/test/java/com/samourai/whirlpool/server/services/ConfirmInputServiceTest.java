package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.integration.AbstractMixIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class ConfirmInputServiceTest extends AbstractMixIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true);
  }

  @Test
  public void confirmInput_shouldSuccessWhenValid() throws Exception {
    Mix mix = __getCurrentMix();
    String mixId = mix.getMixId();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // REGISTER_INPUT
    testUtils.assertMix(0, 1, mix); // mustMix confirming

    // blind bordereau
    byte[] bordereau = ClientUtils.generateBordereau();
    RSABlindingParameters blindingParams = computeBlindingParams(mix);
    byte[] blindedBordereau = clientCryptoService.blind(bordereau, blindingParams);

    // CONFIRM_INPUT
    confirmInputService.confirmInput_webSocket(mixId, blindedBordereau, "userHash", username);

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        cryptoService.signBlindedOutput(blindedBordereau, mix.getKeyPair());

    // REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assertions.assertEquals(0, mix.getReceiveAddresses().size());
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    registerOutputService.registerOutput(mix, unblindedSignedBordereau, receiveAddress, bordereau);
    Assertions.assertEquals(1, mix.getReceiveAddresses().size());

    // TEST

    // VERIFY
    testUtils.assertMix(1, 0, mix); // mustMix confirmed
    Thread.sleep(5000);
  }

  @Test
  public void confirmInput_shouldSuccessWhenValid_soroban() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // REGISTER_INPUT
    SorobanInput sorobanInput = generateSorobanInput(mix.getPool().getPoolId(), false);
    TxOutPoint outPoint = registerInput(mix, username, 999, false, sorobanInput);
    testUtils.assertMix(0, 1, mix); // mustMix confirming

    // blind bordereau
    byte[] bordereau = ClientUtils.generateBordereau();
    RSABlindingParameters blindingParams = computeBlindingParams(mix);
    byte[] blindedBordereau = clientCryptoService.blind(bordereau, blindingParams);

    // CONFIRM_INPUT
    PaymentCode sender = sorobanInput.getSender();
    RegisteredInput registeredInput =
        mix.removeConfirmingInputBySender(sender)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        WhirlpoolErrorCode.SERVER_ERROR,
                        "Confirming input not found: sender=" + sender.toString()));
    confirmInputService.confirmInput(mix, registeredInput, blindedBordereau, "userHash");

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        cryptoService.signBlindedOutput(blindedBordereau, mix.getKeyPair());

    // REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assertions.assertEquals(0, mix.getReceiveAddresses().size());
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    registerOutputService.registerOutput(mix, unblindedSignedBordereau, receiveAddress, bordereau);
    Assertions.assertEquals(1, mix.getReceiveAddresses().size());

    // TEST

    // VERIFY
    testUtils.assertMix(1, 0, mix); // mustMix confirmed
    Thread.sleep(5000);
  }

  @Test
  public void registerInput_shouldQueueWhenInputsSameHash() throws Exception {
    Mix mix = __getCurrentMix();
    String mixId = mix.getMixId();
    Pool pool = mix.getPool();
    String poolId = mix.getPool().getPoolId();

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computePremixBalanceMin(false);

    // same hash
    RpcTransaction rpcTransaction =
        rpcClientService.createAndMockTx(inputAddress, inputBalance, 100, 2);
    TxOutPoint txOutPoint1 = blockchainDataService.getOutPoint(rpcTransaction, 0).get();
    TxOutPoint txOutPoint2 = blockchainDataService.getOutPoint(rpcTransaction, 1).get();

    Assertions.assertEquals(txOutPoint1.getHash(), txOutPoint2.getHash());
    Assertions.assertEquals(0, txOutPoint1.getIndex());
    Assertions.assertEquals(1, txOutPoint2.getIndex());

    // TEST
    registerInput(
            pool,
            "user1",
            signature,
            txOutPoint1.getHash(),
            txOutPoint1.getIndex(),
            false,
            false,
            blockchainDataService.getBlockHeight(),
            null)
        .getOutPoint();
    waitMixLimitsService(mix);
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 1, mix); // confirming

    registerInput(
            pool,
            "user2",
            signature,
            txOutPoint2.getHash(),
            txOutPoint2.getIndex(),
            false,
            false,
            blockchainDataService.getBlockHeight(),
            null)
        .getOutPoint();
    waitMixLimitsService(mix);
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 2, mix); // confirming

    // blind bordereau
    byte[] bordereau = ClientUtils.generateBordereau();
    RSABlindingParameters blindingParams = computeBlindingParams(mix);
    byte[] blindedBordereau = clientCryptoService.blind(bordereau, blindingParams);

    // CONFIRM_INPUT
    confirmInputService.confirmInput_webSocket(mixId, blindedBordereau, "userHash1", "user1");
    confirmInputService.confirmInput_webSocket(mixId, blindedBordereau, "userHash2", "user2");

    // VERIFY
    testUtils.assertMix(1, 0, mix); // 1 mustMix confirmed
    testUtils.assertPool(1, 0, pool); // 1 mustMix queued
  }

  @Test
  public void registerInput_shouldRejectWhenDuplicateUserHash() throws Exception {
    Mix mix = __getCurrentMix();
    String mixId = mix.getMixId();
    Pool pool = mix.getPool();
    String poolId = mix.getPool().getPoolId();
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computePremixBalanceMin(false);

    // different hashs
    RpcTransaction rpcTransaction =
        rpcClientService.createAndMockTx(inputAddress, inputBalance, 100, 2);
    RpcTransaction rpcTransaction2 =
        rpcClientService.createAndMockTx(inputAddress, inputBalance, 100, 2);
    TxOutPoint txOutPoint1 = blockchainDataService.getOutPoint(rpcTransaction, 0).get();
    TxOutPoint txOutPoint2 = blockchainDataService.getOutPoint(rpcTransaction2, 1).get();

    Assertions.assertEquals(txOutPoint1.getHash(), txOutPoint2.getHash());
    Assertions.assertEquals(0, txOutPoint1.getIndex());
    Assertions.assertEquals(1, txOutPoint2.getIndex());

    // TEST
    registerInput(
        pool,
        "user1",
        signature,
        txOutPoint1.getHash(),
        txOutPoint1.getIndex(),
        false,
        false,
        blockchainDataService.getBlockHeight(),
        null);
    waitMixLimitsService(mix);
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 1, mix); // confirming

    registerInput(
        pool,
        "user2",
        signature,
        txOutPoint2.getHash(),
        txOutPoint2.getIndex(),
        false,
        false,
        blockchainDataService.getBlockHeight(),
        null);
    waitMixLimitsService(mix);
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 2, mix); // confirming

    // blind bordereau
    byte[] bordereau = ClientUtils.generateBordereau();
    RSABlindingParameters blindingParams = computeBlindingParams(mix);
    byte[] blindedBordereau = clientCryptoService.blind(bordereau, blindingParams);

    // CONFIRM_INPUT
    confirmInputService.confirmInput_webSocket(mixId, blindedBordereau, "userHash1", "user1");
    confirmInputService.confirmInput_webSocket(
        mixId, blindedBordereau, "userHash1", "user2"); // same userHash
    waitMixLimitsService(mix);

    // VERIFY
    testUtils.assertMix(1, 0, mix); // 1 mustMix confirmed
    testUtils.assertPool(
        1, 0, pool); // 1 mustMix queued because of "Your wallet already registered for this mix"
  }

  @Test
  public void confirmInput_shouldQueueWhenMaxAnonymitySetReached() throws Exception {
    Mix mix = __nextMix(1, 0, 2, __getCurrentPoolId()); // 2 mustMix max
    Pool pool = mix.getPool();

    // 1/2
    registerInputAndConfirmInput(mix, "user1", 999, false, null, null, null);
    testUtils.assertMix(1, 0, mix); // mustMix confirmed
    testUtils.assertPool(0, 0, pool);

    // 2/2
    registerInputAndConfirmInput(mix, "user2", 999, false, null, null, null);
    testUtils.assertMix(2, 0, mix); // mustMix confirmed
    testUtils.assertPool(0, 0, pool);

    // 3/2 => queued
    registerInputAndConfirmInput(mix, "user3", 999, false, null, null, null);
    testUtils.assertMix(2, 0, mix); // mustMix queued
  }

  @Test
  public void confirmInput_shouldQueueWhenMaxMustMixReached() throws Exception {
    Mix mix =
        __nextMix(1, 1, 2, __getCurrentPoolId()); // 2 users max - 1 liquidityMin = 1 mustMix max
    Pool pool = mix.getPool();

    // 1/2 mustMix
    registerInputAndConfirmInput(mix, "mustMix1", 999, false, null, null, null);
    testUtils.assertMix(1, 0, mix); // mustMix confirmed
    testUtils.assertPool(0, 0, pool);

    // 2/2 mustMix => queued
    registerInputAndConfirmInput(mix, "mustMix2", 999, false, null, null, null);
    testUtils.assertMix(1, 0, mix); // mustMix queued
    testUtils.assertPool(1, 0, pool);

    // 1/1 liquidity
    registerInputAndConfirmInput(mix, "liquidity1", 999, true, null, null, null);
    testUtils.assertMix(2, 0, mix); // liquidity confirmed
    testUtils.assertPool(1, 0, pool);
  }

  @Test
  public void confirmInput_shouldRequeueLateConfirmingInputs() throws Exception {
    Mix mix =
        __nextMix(1, 1, 2, __getCurrentPoolId()); // 2 users max - 1 liquidityMin = 1 mustMix max
    Pool pool = mix.getPool();

    // 1/1 mustMix
    registerInput(mix, "mustMix1", 999, false, null);
    testUtils.assertMix(0, 1, mix); // mustMix confirming
    testUtils.assertPool(0, 0, pool);

    // 2/1 mustMix => confirmed
    registerInputAndConfirmInput(mix, "mustMix2", 999, false, null, null, null);
    testUtils.assertMix(1, 1, mix); // mustMix queued
    testUtils.assertPool(0, 0, pool);

    // 1/1 liquidity
    registerInputAndConfirmInput(mix, "liquidity1", 999, true, null, null, null);

    // liquidity confirmed, mustmix1 requeued
    testUtils.assertMix(2, 0, mix);
    testUtils.assertPool(1, 0, pool);
    Assertions.assertTrue(pool.getMustMixQueue().findByUsername("mustMix1").isPresent());
  }
}
