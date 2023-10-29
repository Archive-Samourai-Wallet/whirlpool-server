package com.samourai.whirlpool.server.controllers.web;

import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.controllers.web.beans.WhirlpoolDashboardTemplateModel;
import com.samourai.whirlpool.server.services.PoolService;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class ClientsWebController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT = "/status/clients";

  private WhirlpoolServerConfig serverConfig;
  private PoolService poolService;

  @Autowired
  public ClientsWebController(WhirlpoolServerConfig serverConfig, PoolService poolService) {
    this.serverConfig = serverConfig;
    this.poolService = poolService;
  }

  @RequestMapping(value = ENDPOINT, method = RequestMethod.GET)
  public String clients(Model model) throws Exception {
    new WhirlpoolDashboardTemplateModel(serverConfig, "clients").apply(model);

    List<Pair<String, RegisteredInput>> registeredInputs =
        poolService.getPools().stream()
            .flatMap(
                pool ->
                    Stream.of(pool.getMustMixQueue(), pool.getLiquidityQueue())
                        .flatMap(queue -> queue._getInputs().stream())
                        .map(input -> Pair.of("QUEUED", input)))
            .collect(Collectors.toList());

    poolService.getPools().stream()
        .flatMap(pool -> pool.getCurrentMix().getInputs()._getInputs().stream())
        .forEach(
            input ->
                registeredInputs.add(Pair.of("MIXING", input))); // 'MIXING' used in clients.html

    poolService.getPools().stream()
        .flatMap(pool -> pool.getCurrentMix().getConfirmingInputs()._getInputs().stream())
        .forEach(input -> registeredInputs.add(Pair.of("CONFIRMING", input)));

    model.addAttribute("registeredInputs", registeredInputs);
    return "clients";
  }
}
