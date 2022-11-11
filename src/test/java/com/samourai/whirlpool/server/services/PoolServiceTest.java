package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class PoolServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void findByInputValue() throws Exception {
    Assertions.assertEquals(
        "0.5btc", poolService.findByInputValue(50000000, true).get().getPoolId());
    Assertions.assertFalse(poolService.findByInputValue(50000000, false).isPresent());
    Assertions.assertEquals(
        "0.5btc", poolService.findByInputValue(50001000, false).get().getPoolId());
    Assertions.assertFalse(poolService.findByInputValue(50001000, true).isPresent());

    Assertions.assertEquals(
        "0.05btc", poolService.findByInputValue(5000000, true).get().getPoolId());
    Assertions.assertFalse(poolService.findByInputValue(5000000, false).isPresent());
    Assertions.assertEquals(
        "0.05btc", poolService.findByInputValue(5001000, false).get().getPoolId());
    Assertions.assertFalse(poolService.findByInputValue(5001000, true).isPresent());

    Assertions.assertEquals(
        "0.01btc", poolService.findByInputValue(1000000, true).get().getPoolId());
    Assertions.assertFalse(poolService.findByInputValue(1000000, false).isPresent());
    Assertions.assertEquals(
        "0.01btc", poolService.findByInputValue(1001000, false).get().getPoolId());
    Assertions.assertFalse(poolService.findByInputValue(1001000, true).isPresent());

    Assertions.assertEquals(
        "0.001btc", poolService.findByInputValue(100000, true).get().getPoolId());
    Assertions.assertFalse(poolService.findByInputValue(100000, false).isPresent());
    Assertions.assertEquals(
        "0.001btc", poolService.findByInputValue(101000, false).get().getPoolId());
    Assertions.assertFalse(poolService.findByInputValue(101000, true).isPresent());
  }
}
