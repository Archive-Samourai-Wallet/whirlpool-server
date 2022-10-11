package com.samourai.whirlpool.server.controllers;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.protocol.rest.Tx0DataRequestV2;
import com.samourai.whirlpool.protocol.rest.Tx0DataResponseV2;
import com.samourai.whirlpool.server.controllers.rest.Tx0Controller;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class Tx0ControllerTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired private Tx0Controller tx0Controller;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void tx0Data_noScode() throws Exception {
    boolean cascading = false;
    Tx0DataRequestV2 request = new Tx0DataRequestV2(null, null, cascading);
    Tx0DataResponseV2 response = tx0Controller.tx0Data(null, request);

    // 100% fee (0% SCODE discount)
    for (Tx0DataResponseV2.Tx0Data tx0Data : response.tx0Datas) {
      Assertions.assertEquals(0, tx0Data.feeDiscountPercent);
      Assertions.assertTrue(tx0Data.feeValue > 0);
    }
  }

  @Test
  public void tx0Data_cascading_noFee() throws Exception {
    boolean cascading = true;
    Tx0DataRequestV2 request = new Tx0DataRequestV2(null, null, cascading);
    Tx0DataResponseV2 response = tx0Controller.tx0Data(null, request);

    // 0% fee (100% SCODE discount)
    for (Tx0DataResponseV2.Tx0Data tx0Data : response.tx0Datas) {
      Assertions.assertEquals(100, tx0Data.feeDiscountPercent);
      Assertions.assertTrue(tx0Data.feeValue == 0);
    }
  }
}
