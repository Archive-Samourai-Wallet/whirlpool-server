package com.samourai.whirlpool.server.controllers.web.beans;

import com.samourai.javaserver.web.models.DashboardTemplateModel;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;

public class WhirlpoolDashboardTemplateModel extends DashboardTemplateModel {
  public WhirlpoolDashboardTemplateModel(WhirlpoolServerConfig serverConfig, String currentPage) {
    super(serverConfig.getName(), serverConfig.getName(), currentPage);
  }
}
