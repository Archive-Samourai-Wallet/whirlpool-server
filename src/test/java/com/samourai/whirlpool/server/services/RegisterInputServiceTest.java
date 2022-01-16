package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.InputPool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.integration.AbstractMixIntegrationTest;
import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class RegisterInputServiceTest extends AbstractMixIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired private RegisterInputService registerInputService;

  private static final int MIN_CONFIRMATIONS_MUSTMIX = 11;
  private static final int MIN_CONFIRMATIONS_LIQUIDITY = 22;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.getRegisterInput().setMinConfirmationsMustMix(MIN_CONFIRMATIONS_MUSTMIX);
    serverConfig.getRegisterInput().setMinConfirmationsLiquidity(MIN_CONFIRMATIONS_LIQUIDITY);
    serverConfig.setTestMode(true); // TODO
  }

  private TxOutPoint runTestValidInput(boolean liquidity, boolean spent) throws Exception {
    TxOutPoint txOutPoint = null;
    try {
      Mix mix = __getCurrentMix();
      String poolId = mix.getPool().getPoolId();
      String username = "user1";

      ECKey ecKey =
          ECKey.fromPrivate(
              new BigInteger(
                  "34069012401142361066035129995856280497224474312925604298733347744482107649210"));
      String signature = ecKey.signMessage(poolId);

      long inputBalance = mix.getPool().computePremixBalanceMax(liquidity);
      int confirmations = liquidity ? MIN_CONFIRMATIONS_LIQUIDITY : MIN_CONFIRMATIONS_MUSTMIX;
      txOutPoint =
          createAndMockTxOutPoint(
              new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters()),
              inputBalance,
              confirmations);

      if (spent) {
        rpcClientService.mockSpentOutput(txOutPoint.getHash(), txOutPoint.getIndex());
      }

      // TEST
      registerInputService.registerInput(
          poolId,
          username,
          signature,
          txOutPoint.getHash(),
          txOutPoint.getIndex(),
          liquidity,
          "127.0.0.1");
      waitMixLimitsService(mix);

    } catch (Exception e) {
      if (spent && RegisterInputService.ERROR_ALREADY_SPENT.equals(e.getMessage())) {
        throw e; // expected
      }
      e.printStackTrace();
      Assertions.assertTrue(false);
    }
    return txOutPoint;
  };

  @Test
  public void registerInput_shouldRegisterMustMixWhenValid() throws Exception {
    // TEST
    TxOutPoint txOutPoint = runTestValidInput(false, false);

    // VERIFY
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();

    // mustMix should be registered
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 1, mix); // mustMix confirming
    Assertions.assertTrue(mix.hasConfirmingInput(txOutPoint));
  }

  @Test
  public void registerInput_shouldFailWhenSpent() throws Exception {
    // TEST
    Exception e =
        Assertions.assertThrows(
            IllegalInputException.class,
            () -> {
              runTestValidInput(false, true);
            });
    Assertions.assertEquals(RegisterInputService.ERROR_ALREADY_SPENT, e.getMessage());

    // VERIFY
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();

    // mustMix should NOT be registered
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 0, mix); // no mustMix confirming
  }

  @Test
  public void registerInput_shouldQueueLiquidityWhenValid() throws Exception {
    // TEST
    TxOutPoint txOutPoint = runTestValidInput(true, false);

    // VERIFY
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();
    InputPool liquidityPool = mix.getPool().getLiquidityQueue();

    // liquidity should be queued then invited
    testUtils.assertMix(0, 1, mix);
  }

  @Test
  public void registerInput_shouldQueueMustMixWhenValidAndMixStarted() throws Exception {
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT); // mix already started

    // TEST
    TxOutPoint txOutPoint = runTestValidInput(false, false);

    // VERIFY

    // mustMix should be registered
    testUtils.assertPool(1, 0, pool); // mustMix queued
    testUtils.assertMixEmpty(mix);
    Assertions.assertTrue(mix.getPool().getMustMixQueue().hasInput(txOutPoint));
  }

  @Test
  public void registerInput_shouldQueueLiquidityWhenValidAndMixStarted() throws Exception {
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT); // mix already started

    // TEST
    TxOutPoint txOutPoint = runTestValidInput(true, false);

    // VERIFY
    InputPool liquidityPool = mix.getPool().getLiquidityQueue();

    // liquidity should be queued
    Assertions.assertTrue(liquidityPool.hasInput(txOutPoint));
    testUtils.assertPool(0, 1, pool); // mustMix queued
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldFailWhenInvalidPoolId() throws Exception {
    Mix mix = __getCurrentMix();

    String poolId = "INVALID"; // INVALID
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computePremixBalanceMax(false);
    TxOutPoint txOutPoint = createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    Exception e =
        Assertions.assertThrows(
            IllegalInputException.class,
            () -> {
              registerInputService.registerInput(
                  poolId,
                  username,
                  signature,
                  txOutPoint.getHash(),
                  txOutPoint.getIndex(),
                  false,
                  "127.0.0.1");
            });
    Assertions.assertEquals("Pool not found", e.getMessage());

    // VERIFY
    testUtils.assertPoolEmpty(mix.getPool());
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldQueueInputWhenMixStatusAlreadyStarted() throws Exception {
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());

    // all mixStatus != CONFIRM_INPUT
    for (MixStatus mixStatus : MixStatus.values()) {
      if (!MixStatus.CONFIRM_INPUT.equals(mixStatus)
          && !MixStatus.SUCCESS.equals(mixStatus)
          && !MixStatus.FAIL.equals(mixStatus)) {
        setUp();

        log.info("----- " + mixStatus + " -----");

        Mix mix = __getCurrentMix();
        String mixId = mix.getMixId();
        String poolId = mix.getPool().getPoolId();

        // set status
        mixService.changeMixStatus(mixId, mixStatus);

        // TEST
        String signature = ecKey.signMessage(poolId);
        long inputBalance = mix.getPool().computePremixBalanceMax(false);
        TxOutPoint txOutPoint = createAndMockTxOutPoint(inputAddress, inputBalance);
        registerInputService.registerInput(
            poolId,
            username,
            signature,
            txOutPoint.getHash(),
            txOutPoint.getIndex(),
            false,
            "127.0.0.1");

        // VERIFY
        testUtils.assertPool(1, 0, mix.getPool()); // mustMix queued
        testUtils.assertMixEmpty(mix);
      }
    }
  }

  @Test
  public void registerInput_shouldFailWhenInvalidSignature() throws Exception {
    Mix mix = __getCurrentMix();
    String poolId = mix.getPool().getPoolId();
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = "INVALID";

    long inputBalance = mix.getPool().computePremixBalanceMax(false);
    TxOutPoint txOutPoint = createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    Exception e =
        Assertions.assertThrows(
            IllegalInputException.class,
            () -> {
              registerInputService.registerInput(
                  poolId,
                  username,
                  signature,
                  txOutPoint.getHash(),
                  txOutPoint.getIndex(),
                  false,
                  "127.0.0.1");
            });
    Assertions.assertEquals("Invalid signature", e.getMessage());

    // VERIFY
    testUtils.assertPoolEmpty(mix.getPool());
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldFailWhenInvalidPubkey() throws Exception {
    Mix mix = __getCurrentMix();
    String poolId = mix.getPool().getPoolId();
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        testUtils.generateSegwitAddress(); // INVALID: not related to pubkey
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computePremixBalanceMax(false);
    TxOutPoint txOutPoint = createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    Exception e =
        Assertions.assertThrows(
            IllegalInputException.class,
            () -> {
              registerInputService.registerInput(
                  poolId,
                  username,
                  signature,
                  txOutPoint.getHash(),
                  txOutPoint.getIndex(),
                  false,
                  "127.0.0.1");
            });
    Assertions.assertEquals("Invalid signature", e.getMessage());

    // VERIFY
    testUtils.assertPoolEmpty(mix.getPool());
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldFailWhenDuplicateInputsSameMix() throws Exception {
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();
    String poolId = mix.getPool().getPoolId();

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computePremixBalanceMax(false);
    TxOutPoint txOutPoint = createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    registerInputService.registerInput(
        poolId,
        "user1",
        signature,
        txOutPoint.getHash(),
        txOutPoint.getIndex(),
        false,
        "127.0.0.1");
    waitMixLimitsService(mix);
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 1, mix); // confirming

    registerInputService.registerInput(
        poolId,
        "user2",
        signature,
        txOutPoint.getHash(),
        txOutPoint.getIndex(),
        false,
        "127.0.0.1"); // AlreadyRegisteredInputException thrown in background
    waitMixLimitsService(mix);
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 1, mix); // not confirming twice
  }

  @Test
  public void registerInput_shouldFailWhenBalanceTooLow() throws Exception {
    Mix mix = __getCurrentMix();
    String poolId = mix.getPool().getPoolId();
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computePremixBalanceMin(false) - 1; // BALANCE TOO LOW
    TxOutPoint txOutPoint = createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    Exception e =
        Assertions.assertThrows(
            IllegalInputException.class,
            () -> {
              registerInputService.registerInput(
                  poolId,
                  username,
                  signature,
                  txOutPoint.getHash(),
                  txOutPoint.getIndex(),
                  false,
                  "127.0.0.1");
            });
    Assertions.assertEquals(
        "Invalid input balance (expected: 50000102-50010000, actual:50000101)", e.getMessage());
    waitMixLimitsService(mix);

    // VERIFY
    testUtils.assertPoolEmpty(mix.getPool());
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldRegisterInputWhenBalanceCapTooHighButMaxOk() throws Exception {
    Mix mix = __getCurrentMix();
    String poolId = mix.getPool().getPoolId();
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance =
        mix.getPool().computePremixBalanceCap(false) + 1; // BALANCE CAP TOO HIGH BUT MAX OK
    TxOutPoint txOutPoint = createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    registerInputService.registerInput(
        poolId,
        username,
        signature,
        txOutPoint.getHash(),
        txOutPoint.getIndex(),
        false,
        "127.0.0.1");
    waitMixLimitsService(mix);

    // VERIFY
    testUtils.assertMix(0, 1, mix); // mustMix confirming
  }

  @Test
  public void registerInput_shouldFailWhenBalanceTooHigh() throws Exception {
    Mix mix = __getCurrentMix();
    String poolId = mix.getPool().getPoolId();
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computePremixBalanceMax(false) + 1; // BALANCE TOO HIGH
    TxOutPoint txOutPoint = createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    Exception e =
        Assertions.assertThrows(
            IllegalInputException.class,
            () -> {
              registerInputService.registerInput(
                  poolId,
                  username,
                  signature,
                  txOutPoint.getHash(),
                  txOutPoint.getIndex(),
                  false,
                  "127.0.0.1");
            });
    Assertions.assertEquals(
        "Invalid input balance (expected: 50000102-50010000, actual:50010001)", e.getMessage());
    waitMixLimitsService(mix);

    // VERIFY
    testUtils.assertPoolEmpty(mix.getPool());
    testUtils.assertMixEmpty(mix);
  }

  private void runShouldFailWhenZeroConfirmations(boolean liquidity) throws Exception {
    Mix mix = __getCurrentMix();

    // TEST
    Exception e =
        Assertions.assertThrows(
            IllegalInputException.class,
            () -> {
              registerInput(mix, "user1", 0, liquidity);
            });
    Assertions.assertEquals("Input is not confirmed", e.getMessage());

    // VERIFY
    testUtils.assertPoolEmpty(mix.getPool());
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldFailWhenZeroConfirmationsMustmix() throws Exception {
    runShouldFailWhenZeroConfirmations(false);
  }

  @Test
  public void registerInput_shouldFailWhenZeroConfirmationsLiquidity() throws Exception {
    runShouldFailWhenZeroConfirmations(true);
  }

  private void runShouldFailWhenLessConfirmations(boolean liquidity) throws Exception {
    Mix mix = __getCurrentMix();

    // TEST
    Exception e =
        Assertions.assertThrows(
            IllegalInputException.class,
            () -> {
              registerInput(mix, "user1", MIN_CONFIRMATIONS_MUSTMIX - 1, liquidity);
            });
    Assertions.assertEquals("Input is not confirmed", e.getMessage());

    // VERIFY
    testUtils.assertPoolEmpty(mix.getPool());
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldFailWhenLessConfirmationsMustmix() throws Exception {
    runShouldFailWhenLessConfirmations(false);
  }

  @Test
  public void registerInput_shouldFailWhenLessConfirmationsLiquidity() throws Exception {
    runShouldFailWhenLessConfirmations(true);
  }

  @Test
  public void registerInput_shouldSuccessWhenMoreConfirmations() throws Exception {
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMixEmpty(mix);

    // mustMix
    registerInput(mix, "user1", MIN_CONFIRMATIONS_MUSTMIX + 1, false);
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 1, mix);

    // liquidity
    registerInput(mix, "user2", MIN_CONFIRMATIONS_LIQUIDITY + 1, true);
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 2, mix);
  }
}
