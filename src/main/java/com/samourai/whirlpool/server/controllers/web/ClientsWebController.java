package com.samourai.whirlpool.server.controllers.web;

import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.controllers.web.beans.WhirlpoolDashboardTemplateModel;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
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
  private RegisterInputService registerInputService;

  @Autowired
  public ClientsWebController(
      WhirlpoolServerConfig serverConfig,
      PoolService poolService,
      RegisterInputService registerInputService) {
    this.serverConfig = serverConfig;
    this.poolService = poolService;
    this.registerInputService = registerInputService;
  }

  @RequestMapping(value = ENDPOINT, method = RequestMethod.GET)
  public String clients(Model model) throws Exception {
    new WhirlpoolDashboardTemplateModel(serverConfig, "clients").apply(model);

    // queued websocket inputs
    List<Pair<String, RegisteredInput>> registeredInputs =
        poolService.getPools().stream()
            .flatMap(
                pool ->
                    Stream.of(pool.getMustMixQueue(), pool.getLiquidityQueue())
                        .flatMap(queue -> queue._getInputs().stream())
                        .map(input -> Pair.of("QUEUE", input)))
            .collect(Collectors.toList());

    // queued soroban inputs
    registeredInputs.addAll(
        poolService.getPools().stream()
            .flatMap(
                pool ->
                    Stream.of(pool.getMustMixQueue(), pool.getLiquidityQueue())
                        .flatMap(queue -> queue._getInputsSoroban(registerInputService).stream())
                        .map(input -> Pair.of("QUEUE", input)))
            .collect(Collectors.toList()));

    // mixing inputs
    for (Pool pool : poolService.getPools()) {
      Mix mix = pool.getCurrentMix();
      for (RegisteredInput input : mix.getInputs()._getInputs()) {
        registeredInputs.add(Pair.of("MIX." + mix.getMixStatus().name(), input));
      }
    }

    poolService.getPools().stream()
        .flatMap(pool -> pool.getCurrentMix().getConfirmingInputs()._getInputs().stream())
        .forEach(input -> registeredInputs.add(Pair.of("MIX.CONFIRMING", input)));

    Collections.sort(
        registeredInputs, (a, b) -> a.getRight().getSince() < b.getRight().getSince() ? 1 : -1);

    model.addAttribute("registeredInputs", registeredInputs);
    int nbClientsSoroban = 0, nbClientsClassic = 0;
    for (Pair<String, RegisteredInput> p : registeredInputs) {
      RegisteredInput registeredInput = p.getRight();
      if (registeredInput.isSoroban()) {
        nbClientsSoroban++;
      } else {
        nbClientsClassic++;
      }
    }
    model.addAttribute("nbClientsTotal", nbClientsClassic + nbClientsSoroban);
    model.addAttribute("nbClientsClassic", nbClientsClassic);
    model.addAttribute("nbClientsSoroban", nbClientsSoroban);
    return "clients";
  }
}
