package com.samourai.whirlpool.server.controllers.web;

import com.samourai.javaserver.web.controllers.AbstractMetricsWebController;
import com.samourai.javaserver.web.models.MetricsTemplateModel;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class MetricsWebController extends AbstractMetricsWebController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT_APP = "/status/metrics/app";
  public static final String ENDPOINT_SYSTEM = "/status/metrics/system";

  private WhirlpoolServerConfig serverConfig;

  @Autowired
  public MetricsWebController(WhirlpoolServerConfig serverConfig) {
    this.serverConfig = serverConfig;
  }

  @RequestMapping(value = ENDPOINT_APP, method = RequestMethod.GET)
  public String app(Model model) {
    return metrics(
        model,
        new MetricsTemplateModel(
            serverConfig.getName(),
            serverConfig.getName(),
            "metricsApp",
            serverConfig.getMetricsUrlApp()));
  }

  @RequestMapping(value = ENDPOINT_SYSTEM, method = RequestMethod.GET)
  public String system(Model model) {
    return metrics(
        model,
        new MetricsTemplateModel(
            serverConfig.getName(),
            serverConfig.getName(),
            "metricsSystem",
            serverConfig.getMetricsUrlSystem()));
  }
}
