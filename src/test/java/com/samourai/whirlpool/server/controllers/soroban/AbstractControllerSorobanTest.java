package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.soroban.client.PayloadWithSender;
import com.samourai.soroban.client.SorobanPayload;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractControllerSorobanTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected static final PaymentCode PAYMENTCODE_CLIENT =
      new PaymentCode(
          "PM8TJWqQcDQGTobv3ea6tZRVV1FRgchd64UBj68T6hkgNLzSaC1V1EyNLqj7uzquuiyYcKbKVWkSoNWie8VjJQiZ4EoYbZ9TomyVhQC7jjJ6MQxBtNPr");

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  protected <T extends SorobanPayload> PayloadWithSender<T> withSender(T payload) {
    return new PayloadWithSender<>(payload, PAYMENTCODE_CLIENT);
  }
}
