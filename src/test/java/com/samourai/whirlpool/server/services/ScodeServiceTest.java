package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class ScodeServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String SCODE_FOO_0 = "foo";
  private static final short SCODE_FOO_PAYLOAD = 1234;
  private static final String SCODE_BAR_25 = "bar";
  private static final short SCODE_BAR_PAYLOAD = 5678;
  private static final String SCODE_MIN_50 = "min";
  private static final short SCODE_MIN_PAYLOAD = -32768;
  private static final String SCODE_MAX_80 = "maX";
  private static final short SCODE_MAX_PAYLOAD = 32767;
  private static final String SCODE_EXP_90 = "exp";
  private static final short SCODE_EXP_PAYLOAD = 3344;
  private static final long SCODE_EXP_EXPIRATION = System.currentTimeMillis();

  private WhirlpoolWalletConfig whirlpoolWalletConfig;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    dbService.__reset();

    // scodes
    setScodeConfig(SCODE_FOO_0, SCODE_FOO_PAYLOAD, 0, null);
    setScodeConfig(SCODE_BAR_25, SCODE_BAR_PAYLOAD, 25, null);
    setScodeConfig(SCODE_MIN_50, SCODE_MIN_PAYLOAD, 50, null);
    setScodeConfig(SCODE_MAX_80, SCODE_MAX_PAYLOAD, 80, null);
    setScodeConfig(SCODE_EXP_90, SCODE_EXP_PAYLOAD, 90, SCODE_EXP_EXPIRATION);

    whirlpoolWalletConfig = computeWhirlpoolWalletConfig();
  }

  @Test
  public void getFeePayloadByScode() throws Exception {
    long now = System.currentTimeMillis();
    Assertions.assertEquals(
        0, (int) scodeService.getByScode(SCODE_FOO_0, now).getFeeValuePercent());
    Assertions.assertEquals(
        25, (int) scodeService.getByScode(SCODE_BAR_25, now).getFeeValuePercent());
    Assertions.assertEquals(
        50, (int) scodeService.getByScode(SCODE_MIN_50, now).getFeeValuePercent());
    Assertions.assertEquals(
        80, (int) scodeService.getByScode(SCODE_MAX_80, now).getFeeValuePercent());
    Assertions.assertEquals(
        90, (int) scodeService.getByScode(SCODE_EXP_90, SCODE_EXP_EXPIRATION).getFeeValuePercent());
    Assertions.assertEquals(
        90,
        (int)
            scodeService
                .getByScode(SCODE_EXP_90, SCODE_EXP_EXPIRATION - 1000)
                .getFeeValuePercent());
    Assertions.assertNull(
        scodeService.getByScode(SCODE_EXP_90, SCODE_EXP_EXPIRATION + 1000)); // expired

    // case non-sensitive
    Assertions.assertEquals(80, (int) scodeService.getByScode("MaX", now).getFeeValuePercent());
    Assertions.assertEquals(80, (int) scodeService.getByScode("max", now).getFeeValuePercent());
    Assertions.assertEquals(80, (int) scodeService.getByScode("MAX", now).getFeeValuePercent());
    Assertions.assertEquals(null, scodeService.getByScode("invalid", now));

    Assertions.assertNull(scodeService.getByScode("nonexistent", now));
  }

  @Test
  public void getScodeByFeePayload() throws Exception {
    long now = System.currentTimeMillis();
    Assertions.assertEquals(
        SCODE_FOO_PAYLOAD, (short) scodeService.getByPayload(SCODE_FOO_PAYLOAD, now).getPayload());
    Assertions.assertEquals(
        SCODE_BAR_PAYLOAD, (short) scodeService.getByPayload(SCODE_BAR_PAYLOAD, now).getPayload());
    Assertions.assertEquals(
        SCODE_MIN_PAYLOAD, (short) scodeService.getByPayload(SCODE_MIN_PAYLOAD, now).getPayload());
    Assertions.assertEquals(
        SCODE_MAX_PAYLOAD, (short) scodeService.getByPayload(SCODE_MAX_PAYLOAD, now).getPayload());
    Assertions.assertEquals(
        SCODE_EXP_PAYLOAD,
        (short) scodeService.getByPayload(SCODE_EXP_PAYLOAD, SCODE_EXP_EXPIRATION).getPayload());
    Assertions.assertEquals(
        SCODE_EXP_PAYLOAD,
        (short)
            scodeService.getByPayload(SCODE_EXP_PAYLOAD, SCODE_EXP_EXPIRATION - 1000).getPayload());
    Assertions.assertNull(
        scodeService.getByPayload(SCODE_EXP_PAYLOAD, SCODE_EXP_EXPIRATION + 1000));

    Assertions.assertNull(scodeService.getByPayload((short) 0, now));
    Assertions.assertNull(scodeService.getByPayload((short) -1, now));
  }
}
