package com.samourai.whirlpool.server.controllers.web;

import com.samourai.wallet.util.RandomUtil;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.controllers.web.beans.ClientInput;
import com.samourai.whirlpool.server.controllers.web.beans.ClientInputSort;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class ClientsWebControllerTest extends AbstractIntegrationTest {

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
