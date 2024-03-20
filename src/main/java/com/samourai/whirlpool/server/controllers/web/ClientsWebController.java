package com.samourai.whirlpool.server.controllers.web;

import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.controllers.web.beans.ClientInput;
import com.samourai.whirlpool.server.controllers.web.beans.WhirlpoolDashboardTemplateModel;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class ClientsWebController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT = "/status/clients";
  private static final int PAGE_SIZE = 200;

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
  public String clients(
      Model model,
      @PageableDefault(size = PAGE_SIZE, sort = "since", direction = Sort.Direction.DESC)
          Pageable pageable)
      throws Exception {
    new WhirlpoolDashboardTemplateModel(serverConfig, "clients").apply(model);

    List<ClientInput> registeredInputs = new LinkedList<>();
    for (Pool pool : poolService.getPools()) {
      // queued classic+soroban
      registeredInputs.addAll(
          Stream.of(pool.getMustMixQueue(), pool.getLiquidityQueue())
              .flatMap(
                  queue ->
                      Stream.of(queue._getInputsSoroban().stream(), queue._getInputs().stream()))
              .flatMap(stream -> stream)
              .map(input -> new ClientInput(input, null, false))
              .collect(Collectors.toList()));

      Mix mix = pool.getCurrentMix();
      // confirmed
      for (RegisteredInput input : mix.getInputs()._getInputs()) {
        registeredInputs.add(new ClientInput(input, mix, true));
      }
      // confirming
      for (RegisteredInput input : mix.getConfirmingInputs()._getInputs()) {
        registeredInputs.add(new ClientInput(input, mix, false));
      }
    }

    Comparator<ClientInput> comparator = ClientInput.computeComparator(pageable);
    Collections.sort(registeredInputs, comparator);

    // paginate
    Page<ClientInput> page = Utils.paginateList(pageable, registeredInputs);

    model.addAttribute("page", page);
    int nbClientsSoroban = 0, nbClientsClassic = 0;
    for (ClientInput clientInput : registeredInputs) {
      RegisteredInput registeredInput = clientInput.registeredInput;
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
