package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.FailReason;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class MixServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void isConfirmInputReady_noLiquidityMin() throws Exception {
    MixService spyMixService = Mockito.spy(mixService);
    long denomination = 200000000;
    long feeValue = 10000000;
    long minerFeeMin = 100;
    long minerFeeCap = 9500;
    long minerFeeMax = 10000;
    long minRelayFee = 510;
    long surgeRelayFee = 100;
    int mustMixMin = 1;
    int liquidityMin = 0;
    int anonymitySet = 2;
    Mix mix =
        __nextMix(
            denomination,
            feeValue,
            minerFeeMin,
            minerFeeCap,
            minerFeeMax,
            minRelayFee,
            surgeRelayFee,
            mustMixMin,
            liquidityMin,
            anonymitySet,
            0);
    String mixId = mix.getMixId();

    long mustMixValue = 200000400;

    // initial: accept mustMix
    Assertions.assertFalse(mix.hasMinMustMixAndFeeReached());
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(0, mix.getNbInputsMustMix());
    Assertions.assertEquals(0, mix.getNbInputsLiquidities());
    Assertions.assertEquals(0, mix.getSurge());
    Assertions.assertEquals(2, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(0, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(true, false, mix);

    // 1 mustMix, minerFee not reached => accept mustMix
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "mustMix1",
            false,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "mustMix1", "".getBytes(), "userHash1");
    Assertions.assertFalse(mix.hasMinMustMixAndFeeReached());
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(1, mix.getNbInputsMustMix());
    Assertions.assertEquals(0, mix.getNbInputsLiquidities());
    Assertions.assertEquals(0, mix.getSurge());
    Assertions.assertEquals(1, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(0, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(true, false, mix);

    // 2 mustMix, minerFee reached => ready
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "mustMix2",
            false,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "mustMix2", "".getBytes(), "userHash2");
    Assertions.assertTrue(mix.hasMinMustMixAndFeeReached());
    Assertions.assertTrue(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(0, mix.getNbInputsLiquidities());
    Assertions.assertEquals(0, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(0, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertTrue(mix.isAnonymitySetReached());
    checkAccepts(false, false, mix);
  }

  @Test
  public void isConfirmInputReady_withLiquidityBefore() throws Exception {
    MixService spyMixService = Mockito.spy(mixService);
    long denomination = 200000000;
    long feeValue = 10000000;
    long minerFeeMin = 100;
    long minerFeeCap = 9500;
    long minerFeeMax = 10000;
    long minRelayFee = 510;
    long surgeRelayFee = 100;
    int mustMixMin = 1;
    int liquidityMin = 1;
    int anonymitySet = 4;
    Mix mix =
        __nextMix(
            denomination,
            feeValue,
            minerFeeMin,
            minerFeeCap,
            minerFeeMax,
            minRelayFee,
            surgeRelayFee,
            mustMixMin,
            liquidityMin,
            anonymitySet,
            0);
    String mixId = mix.getMixId();

    long mustMixValue = 200000255;

    // initial => accept mustMix & liquidity
    checkAccepts(true, true, mix);
    Assertions.assertFalse(mix.hasMinMustMixAndFeeReached());
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(0, mix.getNbInputsMustMix());
    Assertions.assertEquals(0, mix.getNbInputsLiquidities());
    Assertions.assertEquals(0, mix.getSurge());
    Assertions.assertEquals(3, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(1, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(true, true, mix);

    // 1 liquidity, minMustMix not reached => accept mustMix
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity1",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity1", "".getBytes(), "liquidity1");
    Assertions.assertFalse(mix.hasMinMustMixAndFeeReached());
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(0, mix.getNbInputsMustMix());
    Assertions.assertEquals(1, mix.getNbInputsLiquidities());
    Assertions.assertEquals(0, mix.getSurge());
    Assertions.assertEquals(3, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(0, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(true, false, mix);

    // 1 liquidity + 1 mustMix, minMustMix reached but minerFeeMix not reached => accept mustMix
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "mustMix1",
            false,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "mustMix1", "".getBytes(), "mustMix1");
    Assertions.assertFalse(mix.hasMinMustMixAndFeeReached());
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(1, mix.getNbInputsMustMix());
    Assertions.assertEquals(1, mix.getNbInputsLiquidities());
    Assertions.assertEquals(0, mix.getSurge());
    Assertions.assertEquals(2, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(0, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(true, false, mix);

    // 1 liquidity + 2 mustMix, minMustMix reached + minerFeeMix reached => accept mustMix and
    // liquidity
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "mustMix2",
            false,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "mustMix2", "".getBytes(), "mustMix2");
    Assertions.assertTrue(mix.hasMinMustMixAndFeeReached());
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(1, mix.getNbInputsLiquidities());
    Assertions.assertEquals(0, mix.getSurge());
    Assertions.assertEquals(1, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(1, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(true, true, mix);

    // 2 liquidity + 2 mustMix => ready
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity2",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity2", "".getBytes(), "liquidity2");
    Assertions.assertTrue(mix.hasMinMustMixAndFeeReached());
    Assertions.assertTrue(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(2, mix.getNbInputsLiquidities());
    Assertions.assertEquals(0, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(0, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertTrue(mix.isAnonymitySetReached());
  }

  @Test
  public void isConfirmInputReady_withSurge() throws Exception {
    MixService spyMixService = Mockito.spy(mixService);
    long denomination = 200000000;
    long feeValue = 10000000;
    long minerFeeMin = 100;
    long minerFeeCap = 9500;
    long minerFeeMax = 10000;
    long minRelayFee = 510;
    long surgeRelayFee = 100;
    int mustMixMin = 2;
    int liquidityMin = 1;
    int anonymitySet = 5;
    int surge = 2;
    Mix mix =
        __nextMix(
            denomination,
            feeValue,
            minerFeeMin,
            minerFeeCap,
            minerFeeMax,
            minRelayFee,
            surgeRelayFee,
            mustMixMin,
            liquidityMin,
            anonymitySet,
            surge);
    String mixId = mix.getMixId();

    long mustMixValue = 200000555; // high minerFee to pay surges

    // 1 mustMix, minMustMix reached but minerFeeMix not reached => accept mustMix & liquidities
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "mustMix1",
            false,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "mustMix1", "".getBytes(), "mustMix1");
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(1, mix.getNbInputsMustMix());
    Assertions.assertEquals(0, mix.getNbInputsLiquidities());
    Assertions.assertEquals(0, mix.getSurge());
    Assertions.assertEquals(3, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(1, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(true, true, mix);

    // 2 mustMix, minerFeeMix reached => accept liquidity & surges
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "mustMix2",
            false,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "mustMix2", "".getBytes(), "mustMix2");
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(0, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(5, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 1 liquidity => accept liquidity & surges
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity1",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity1", "".getBytes(), "liquidity1");
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(1, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(4, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 2 liquidities => accept liquidity & surges
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity2",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity2", "".getBytes(), "liquidity2");
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(2, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(3, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 3 liquidities => accept surges & ready
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity3",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity3", "".getBytes(), "liquidity3");
    Assertions.assertTrue(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(3, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(2, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertTrue(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 4 liquidities => accept surges & ready
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity4",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity4", "".getBytes(), "liquidity4");
    Assertions.assertTrue(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(4, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(1, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertTrue(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 5 liquidities => full
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity5",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity5", "".getBytes(), "liquidity5");
    Assertions.assertTrue(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(5, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(0, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertTrue(mix.isAnonymitySetReached());
    checkAccepts(false, false, mix);
  }

  @Test
  public void isConfirmInputReady_downSurgeOnMustMixDisconnect() throws Exception {
    MixService spyMixService = Mockito.spy(mixService);
    long denomination = 200000000;
    long feeValue = 10000000;
    long minerFeeMin = 100;
    long minerFeeCap = 9500;
    long minerFeeMax = 10000;
    long minRelayFee = 510;
    long surgeRelayFee = 100;
    int mustMixMin = 2;
    int liquidityMin = 1;
    int anonymitySet = 5;
    int surge = 2;
    Mix mix =
        __nextMix(
            denomination,
            feeValue,
            minerFeeMin,
            minerFeeCap,
            minerFeeMax,
            minRelayFee,
            surgeRelayFee,
            mustMixMin,
            liquidityMin,
            anonymitySet,
            surge);
    String mixId = mix.getMixId();

    long mustMixValue = 200000555; // high minerFee to pay surges

    // 1 mustMix, minMustMix reached but minerFeeMix not reached => accept mustMix & liquidities
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "mustMix1",
            false,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "mustMix1", "".getBytes(), "mustMix1");
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(1, mix.getNbInputsMustMix());
    Assertions.assertEquals(0, mix.getNbInputsLiquidities());
    Assertions.assertEquals(0, mix.getSurge());
    Assertions.assertEquals(3, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(1, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(true, true, mix);

    //////////////////////////////////////// >

    // 2 mustMix, minerFeeMix reached => accept liquidity & surges
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "mustMix2",
            false,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "mustMix2", "".getBytes(), "mustMix2");
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(0, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(5, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 1 liquidity => accept liquidity & surges
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity1",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity1", "".getBytes(), "liquidity1");
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(1, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(4, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 2 liquidities => accept liquidity & surges
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity2",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity2", "".getBytes(), "liquidity2");
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(2, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(3, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 3 liquidities => accept surges & ready
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity3",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity3", "".getBytes(), "liquidity3");
    Assertions.assertTrue(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(3, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(2, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertTrue(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 4 liquidities => accept surges & ready
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity4",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity4", "".getBytes(), "liquidity4");
    Assertions.assertTrue(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(4, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(1, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertTrue(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    //////////////////////////////////////// <

    // disconnect mustMix: 1 mustMix + 4 liquidities => disconnect surges & reset surge & accept
    // mustMix
    mixService.onClientDisconnect("mustMix2");
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(1, mix.getNbInputsMustMix());
    Assertions.assertEquals(1, mix.getNbInputsLiquidities());
    Assertions.assertEquals(0, mix.getSurge());
    Assertions.assertEquals(3, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(0, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(true, false, mix);

    //////////////////////////////////////// >

    // 2 mustMix + 1 liquidity, minerFeeMix reached => accept liquidity & surges
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "mustMix2",
            false,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "mustMix2", "".getBytes(), "mustMix2");
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(1, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(4, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 2 liquidities => accept liquidity & surges
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity11",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity11", "".getBytes(), "liquidity11");
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(2, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(3, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertFalse(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 3 liquidities => accept & surges & ready
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity22",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity22", "".getBytes(), "liquidity22");
    Assertions.assertTrue(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(3, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(2, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertTrue(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 4 liquidities => accept surges & ready
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity33",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity33", "".getBytes(), "liquidity33");
    Assertions.assertTrue(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(4, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(1, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertTrue(mix.isAnonymitySetReached());
    checkAccepts(false, true, mix);

    // 2 mustMix + 5 liquidities => full
    mix.registerConfirmingInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            "liquidity44",
            true,
            generateOutPoint(mustMixValue),
            "127.0.0.1",
            null));
    mixService.confirmInput(mixId, "liquidity44", "".getBytes(), "liquidity44");
    Assertions.assertTrue(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputsMustMix());
    Assertions.assertEquals(5, mix.getNbInputsLiquidities());
    Assertions.assertEquals(2, mix.getSurge());
    Assertions.assertEquals(0, mix.getAvailableSlotsMustMix());
    Assertions.assertEquals(0, mix.getAvailableSlotsLiquidityAndSurge());
    Assertions.assertTrue(mix.isAnonymitySetReached());
    checkAccepts(false, false, mix);
  }

  @Test
  public void isConfirmInputReady_spentWhileRegisterInput() throws Exception {
    MixService spyMixService = Mockito.spy(mixService);
    mixService = spyMixService;

    long denomination = 200000000;
    long feeValue = 10000000;
    long minerFeeMin = 100;
    long minerFeeCap = 9500;
    long minerFeeMax = 10000;
    long minRelayFee = 510;
    long surgeRelayFee = 100;
    int mustMixMin = 1;
    int liquidityMin = 0;
    int anonymitySet = 2;
    Mix mix =
        __nextMix(
            denomination,
            feeValue,
            minerFeeMin,
            minerFeeCap,
            minerFeeMax,
            minRelayFee,
            surgeRelayFee,
            mustMixMin,
            liquidityMin,
            anonymitySet,
            0);

    long mustMixValue = 200000555; // high minerFee to pay surges

    // 0 mustMix => accept mustMix
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    checkAccepts(true, false, mix);

    // 1 mustMix => accept mustMix + liquidity
    ConfirmedInput mustMix1 =
        new ConfirmedInput(
            new RegisteredInput(
                mix.getPool().getPoolId(),
                "mustMix1",
                false,
                generateOutPoint(mustMixValue),
                "127.0.0.1",
                null),
            "userHash1");
    mix.registerInput(mustMix1);
    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix));
    checkAccepts(true, true, mix);

    // 2 mustMix => ready
    ConfirmedInput mustMix2 =
        new ConfirmedInput(
            new RegisteredInput(
                mix.getPool().getPoolId(),
                "mustMix2",
                false,
                generateOutPoint(mustMixValue),
                "127.0.0.1",
                null),
            "userHash2");
    mix.registerInput(mustMix2);
    Assertions.assertTrue(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputs());
    checkAccepts(false, false, mix);

    String blameIdentifierMustMix1 = Utils.computeBlameIdentitifer(mustMix1.getRegisteredInput());
    Assertions.assertTrue(dbService.findBlames(blameIdentifierMustMix1).isEmpty()); // no blame

    // mustMix spent in meantime => false
    TxOutPoint out1 = mustMix1.getRegisteredInput().getOutPoint();
    rpcClientService.mockSpentOutput(out1.getHash(), out1.getIndex());

    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix)); // mix not valid anymore
    Assertions.assertEquals(1, mix.getNbInputs());

    // no blame as mix was not started yet
    Assertions.assertEquals(dbService.findBlames(blameIdentifierMustMix1).size(), 0);

    // 2 mustMix => true
    ConfirmedInput mustMix3 =
        new ConfirmedInput(
            new RegisteredInput(
                mix.getPool().getPoolId(),
                "mustMix3",
                false,
                generateOutPoint(mustMixValue),
                "127.0.0.1",
                null),
            "userHash3");
    mix.registerInput(mustMix3);
    Assertions.assertTrue(spyMixService.isConfirmInputReady(mix));
    Assertions.assertEquals(2, mix.getNbInputs());

    // REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);

    // mustMix spent in meantime => false
    TxOutPoint out3 = mustMix3.getRegisteredInput().getOutPoint();
    rpcClientService.mockSpentOutput(out3.getHash(), out3.getIndex());

    Assertions.assertFalse(spyMixService.isConfirmInputReady(mix)); // mix not valid + trigger fail

    // mix failed
    Assertions.assertEquals(MixStatus.FAIL, mix.getMixStatus());
    Assertions.assertEquals(FailReason.SPENT, mix.getFailReason());
    Assertions.assertEquals(out3.getHash() + ":" + out3.getIndex(), mix.getFailInfo());

    // blame as mix was already started
    String blameIdentifierMustMix3 = Utils.computeBlameIdentitifer(mustMix3.getRegisteredInput());
    Assertions.assertEquals(dbService.findBlames(blameIdentifierMustMix3).size(), 1);
  }

  private TxOutPoint generateOutPoint(long value) {
    TxOutPoint txOutPoint =
        new TxOutPoint(
            Utils.getRandomString(65),
            0,
            value,
            99,
            null,
            testUtils.generateSegwitAddress().getBech32AsString());
    return txOutPoint;
  }

  private void checkAccepts(boolean mustMix, boolean liquidity, Mix mix) {
    // try mustMix
    try {
      RegisteredInput input =
          new RegisteredInput(
              mix.getPool().getPoolId(),
              "username" + Utils.getRandomString(10),
              false,
              generateOutPoint(mix.getPool().computePremixBalanceMax(false)),
              "127.0.0.1",
              null);
      mix.hasAvailableSlotFor(input);
      if (!mustMix) {
        Assertions.fail("should not accept mustMix");
      }
    } catch (QueueInputException e) {
      if (!mustMix && !liquidity) {
        Assertions.assertEquals("Current mix is full", e.getMessage());
        return;
      }
      if (!mustMix) {
        Assertions.assertEquals("Current mix is full for mustMix", e.getMessage());
      } else {
        Assertions.fail("should accept mustMix");
      }
    }

    // try liquidity
    try {
      RegisteredInput input =
          new RegisteredInput(
              mix.getPool().getPoolId(),
              "username" + Utils.getRandomString(10),
              true,
              generateOutPoint(mix.getPool().computePremixBalanceMin(true)),
              "127.0.0.1",
              null);
      mix.hasAvailableSlotFor(input);
      if (!liquidity) {
        Assertions.fail("should not accept liquidity");
      }
    } catch (QueueInputException e) {
      if (!liquidity) {
        Assertions.assertEquals("Current mix is full for liquidity", e.getMessage());
      } else {
        Assertions.fail("should accept liquidity");
      }
    }
  }
}
