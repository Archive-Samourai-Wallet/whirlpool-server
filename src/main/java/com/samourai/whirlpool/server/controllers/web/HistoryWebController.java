package com.samourai.whirlpool.server.controllers.web;

import com.google.common.collect.Lists;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.persistence.to.MixLogTO;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import com.samourai.whirlpool.server.services.DbService;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class HistoryWebController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT = "/status/history";
  private static final String URL_EXPLORER_TESTNET = "https://blockstream.info/testnet/tx/";
  private static final String URL_EXPLORER_MAINNET = "https://blockstream.info/tx/";

  private DbService dbService;
  private WhirlpoolServerConfig whirlpoolServerConfig;

  @Autowired
  public HistoryWebController(DbService dbService, WhirlpoolServerConfig whirlpoolServerConfig) {
    this.dbService = dbService;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
  }

  @RequestMapping(value = ENDPOINT, method = RequestMethod.GET)
  public String history(Model model) throws Exception {
    Iterable<MixTO> mixs = Lists.newArrayList(dbService.findMixs());
    model.addAttribute("mixs", mixs);
    model.addAttribute("urlExplorer", computeUrlExplorer());
    model.addAttribute("mixStats", dbService.getMixStats());

    // getters used in template
    if (false) {
      for (MixTO mixTO : mixs) {
        mixTO.getMixId();
        mixTO.getAnonymitySet();
        mixTO.getNbMustMix();
        mixTO.getNbLiquidities();
        mixTO.getFeesAmount();
        mixTO.getFeesPrice();
        mixTO.getMixStatus();
        mixTO.getFailReason();
        MixLogTO mixLogTO = mixTO.getMixLog();
        mixLogTO.getTxid();
        mixLogTO.getRawTx();
      }
    }
    return "history";
  }

  private String computeUrlExplorer() {
    return (whirlpoolServerConfig.isTestnet() ? URL_EXPLORER_TESTNET : URL_EXPLORER_MAINNET);
  }
}
