package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.persistence.repositories.BanRepository;
import com.samourai.whirlpool.server.persistence.repositories.BlameRepository;
import com.samourai.whirlpool.server.persistence.to.BanTO;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.sql.Timestamp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class BanServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired private BlameRepository blameRepository;
  @Autowired private BlameService blameService;
  @Autowired private BanService banService;
  @Autowired private BanRepository banRepository;

  private static final int EXPIRATION_MS = 1000 * 1000;

  // BLAME

  @Test
  public void blame_and_ban_mustmix() throws Exception {
    // server.ban.blames = 2
    // server.ban.period = 1OO
    // server.ban.expiration = 1OOO
    Mix mix = __getCurrentMix();

    final String UTXO_HASH = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    final long UTXO_INDEX = 2;

    boolean liquidity = false; // mustmix
    RegisteredInput registeredInput =
        testUtils
            .computeConfirmedInput(mix.getPool().getPoolId(), UTXO_HASH, UTXO_INDEX, liquidity)
            .getRegisteredInput();

    // not banned yet
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // blame 1/2 => not banned yet
    blameService.blame(registeredInput, BlameReason.DISCONNECT, mix);
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // blame 2/2 => banned
    blameService.blame(registeredInput, BlameReason.DISCONNECT, mix);
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // inputs from same HASH are banned too
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    // other inputs are not banned
    Assertions.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());

    // ban still active before expiration
    Timestamp beforeExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS - 10000);
    Assertions.assertTrue(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, beforeExpiration).isPresent());

    // other inputs are not banned
    Assertions.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());

    // ban disabled after expiration
    Timestamp afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS);
    Assertions.assertFalse(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());

    afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS + 10000);
    Assertions.assertFalse(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());
  }

  @Test
  public void blame_and_ban_liquidity() throws Exception {
    // server.ban.blames = 2
    // server.ban.period = 1OO
    // server.ban.duration = 1OOO
    Mix mix = __getCurrentMix();

    final String UTXO_HASH = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    final long UTXO_INDEX = 2;

    boolean liquidity = true; // liquidity
    RegisteredInput registeredInput =
        testUtils
            .computeConfirmedInput(mix.getPool().getPoolId(), UTXO_HASH, UTXO_INDEX, liquidity)
            .getRegisteredInput();

    // not banned yet
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // blame 1/2 => not banned yet
    blameService.blame(registeredInput, BlameReason.DISCONNECT, mix);
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // blame 2/2 => banned
    blameService.blame(registeredInput, BlameReason.DISCONNECT, mix);
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // other inputs are not banned
    Assertions.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());

    // inputs from same HASH are not banned
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    // ban still active before expiration
    Timestamp beforeExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS - 10000);
    Assertions.assertTrue(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, beforeExpiration).isPresent());

    // other inputs are not banned
    Assertions.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());

    // ban disabled after expiration
    Timestamp afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS);
    Assertions.assertFalse(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());

    afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS + 10000);
    Assertions.assertFalse(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());
  }

  @Test
  public void ban_recidivism() throws Exception {
    // server.ban.blames = 2
    // server.ban.period = 1OO
    // server.ban.duration = 1OOO
    long expectedDuration = 1000 * 1000;
    final String UTXO_HASH = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    final long UTXO_INDEX = 2;
    String identifier = Utils.computeBlameIdentitifer(UTXO_HASH, UTXO_INDEX, false);
    long dayMs = (86400 * 1000);

    int recidivismFactor = serverConfig.getBan().getRecidivismFactor();
    Assertions.assertEquals(2, recidivismFactor);

    // ban 1
    Timestamp twoDaysAgo = new Timestamp(System.currentTimeMillis() - 2 * dayMs);
    BanTO banTO1 = banService.banTemporary(twoDaysAgo, identifier, null, "test1");
    log.debug("banTO1=" + banTO1);
    Assertions.assertEquals(expectedDuration, banTO1.getDuration());

    // ban
    Timestamp yesterday = new Timestamp(System.currentTimeMillis() - 2 * dayMs);
    BanTO banTO2 = banService.banTemporary(yesterday, identifier, null, "test2");
    Assertions.assertEquals(
        expectedDuration * recidivismFactor, banTO2.getDuration()); // duration doubled

    // ban
    BanTO banTO3 = banService.banTemporary(identifier, null, "test2");
    Assertions.assertEquals(
        expectedDuration * recidivismFactor * recidivismFactor,
        banTO3.getDuration()); // duration doubled
  }

  // PERMANENT BAN

  /*@Test
  public void permanent_ban_mustmix() throws Exception {
    // server.ban.blames = 2
    // server.ban.period = 1OO
    // server.ban.expiration = 1OOO

    final String UTXO_HASH = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    final long UTXO_INDEX = 2;

    boolean liquidity = false; // mustmix
    ConfirmedInput confirmedInput =
        testUtils.computeConfirmedInput(UTXO_HASH, UTXO_INDEX, liquidity);
    String identifier = Utils.computeBlameIdentitifer(confirmedInput);

    // not banned yet
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // permanent ban
    banService.banPermanent(identifier, null, null);

    // banned
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // other inputs are not banned
    Assertions.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());

    // inputs from same HASH are banned too
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    // ban still active before expiration
    Timestamp beforeExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS - 10000);
    Assertions.assertTrue(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, beforeExpiration).isPresent());

    // ban still after expiration
    Timestamp afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS);
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());

    afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS + 10000);
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());

    // other inputs are not banned
    Assertions.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());
  }

  @Test
  public void permanent_ban_liquidity() throws Exception {
    // server.ban.blames = 2
    // server.ban.period = 1OO
    // server.ban.expiration = 1OOO

    final String UTXO_HASH = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    final long UTXO_INDEX = 2;

    boolean liquidity = true; // liquidity
    ConfirmedInput confirmedInput =
        testUtils.computeConfirmedInput(UTXO_HASH, UTXO_INDEX, liquidity);
    String identifier = Utils.computeBlameIdentitifer(confirmedInput);

    // not banned yet
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // permanent ban
    banService.banPermanent(identifier, null, null);

    // banned
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // other inputs are not banned
    Assertions.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());

    // inputs from same HASH are not
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    // ban still active before expiration
    Timestamp beforeExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS - 10000);
    Assertions.assertTrue(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, beforeExpiration).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    // ban still after expiration
    Timestamp afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS);
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS + 10000);
    Assertions.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assertions.assertFalse(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    // other inputs are not banned
    Assertions.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());
  }*/
}
