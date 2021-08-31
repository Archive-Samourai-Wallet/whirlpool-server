package com.samourai.whirlpool.server.utils;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class UtilsTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void computeBlameIdentitifer_mustmix() {
    ConfirmedInput confirmedInput =
        testUtils.computeConfirmedInput(
            "poolId", "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", 2, false);

    // mustmix => should ban TX0
    String expected = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    String actual = Utils.computeBlameIdentitifer(confirmedInput);
    Assert.assertEquals(actual, actual);
  }

  @Test
  public void computeBlameIdentitifer_liquidity() {
    ConfirmedInput confirmedInput =
        testUtils.computeConfirmedInput(
            "poolId", "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", 2, true);

    // liquidity => should ban UTXO
    String expected = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187:2";
    String actual = Utils.computeBlameIdentitifer(confirmedInput);
    Assert.assertEquals(actual, actual);
  }
}
