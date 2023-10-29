package com.samourai.whirlpool.server.controllers.web;

import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.util.urlStatus.UpStatus;
import com.samourai.wallet.util.urlStatus.UpStatusPool;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.controllers.web.beans.WhirlpoolDashboardTemplateModel;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class SorobanWebController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT = "/status/soroban";

  private WhirlpoolServerConfig serverConfig;
  private UpStatusPool upStatusPool;

  @Autowired
  public SorobanWebController(WhirlpoolServerConfig serverConfig) {
    this.serverConfig = serverConfig;
    this.upStatusPool = RpcSession.getUpStatusPool();
  }

  @RequestMapping(value = ENDPOINT, method = RequestMethod.GET)
  public String clients(Model model) throws Exception {

    new WhirlpoolDashboardTemplateModel(serverConfig, "soroban").apply(model);

    Collection<UpStatus> upStatuses = upStatusPool.getList();
    int nbServersUp = (int) upStatuses.stream().filter(s -> s.isUp()).count();
    int nbServersDown = (int) upStatuses.stream().filter(s -> !s.isUp()).count();

    model.addAttribute("upStatuses", upStatuses);
    model.addAttribute("nbUp", nbServersUp);
    model.addAttribute("nbDown", nbServersDown);
    return "soroban";
  }

  private Long toSeconds(Long milliseconds) {
    if (milliseconds == null) {
      return null;
    }
    return milliseconds / 1000;
  }
}
