package com.samourai.whirlpool.server.controllers.web;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.wallet.util.RandomUtil;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.controllers.web.beans.ClientInput;
import com.samourai.whirlpool.server.controllers.web.beans.ClientInputSort;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class ClientsWebControllerTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void computeComparator() {
    List<ClientInput> clientInputs =
        Arrays.asList(generateClientInput(), generateClientInput(), generateClientInput());
    for (ClientInputSort sort : ClientInputSort.values()) {
      Pageable pageable = PageRequest.of(0, 3, Sort.by(sort.name()));
      Comparator comparator = ClientInput.computeComparator(pageable);
      clientInputs.sort(comparator);
      log.debug("sort[" + sort.name() + "] " + clientInputs);
    }
  }

  private ClientInput generateClientInput() {
    int randInt = RandomUtil.getInstance().nextInt(100);
    RegisteredInput registeredInput =
        new RegisteredInput(
            "0.001btc",
            "mustMix" + randInt,
            false,
            generateOutPoint(1000),
            false,
            "userHash" + randInt,
            null);
    return new ClientInput(registeredInput, null, false);
  }
}
