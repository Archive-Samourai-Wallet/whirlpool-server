package com.samourai.whirlpool.server.controllers.web;

import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.controllers.web.beans.WhirlpoolDashboardTemplateModel;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class MetricsWebController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT_WHIRLPOOL = "/status/metrics/whirlpool";
  public static final String ENDPOINT_SYSTEM = "/status/metrics/system";

  private WhirlpoolServerConfig serverConfig;

  @Autowired
  public MetricsWebController(WhirlpoolServerConfig serverConfig) {
    this.serverConfig = serverConfig;
  }

  @RequestMapping(value = ENDPOINT_WHIRLPOOL, method = RequestMethod.GET)
  public String whirlpool(Model model) {
    return doMetrics(model, serverConfig.getMetricsUrlWhirlpool(), "metricsWhirlpool");
  }

  @RequestMapping(value = ENDPOINT_SYSTEM, method = RequestMethod.GET)
  public String system(Model model) {
    return doMetrics(model, serverConfig.getMetricsUrlSystem(), "metricsSystem");
  }

  private String doMetrics(Model model, String url, String currentPage) {
    model.addAttribute("metricsUrl", url);
    model.addAttribute("myCurrentPage", currentPage);
    new WhirlpoolDashboardTemplateModel(serverConfig).apply(model);
    return "metrics";
  }
}
