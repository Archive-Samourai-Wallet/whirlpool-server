package com.samourai.whirlpool.server;

import com.samourai.javaserver.config.ServerConfig;
import com.samourai.javaserver.run.ServerApplication;
import com.samourai.wallet.api.backend.MinerFee;
import com.samourai.wallet.xmanagerClient.XManagerClient;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.BackendService;
import com.samourai.whirlpool.server.services.ExportService;
import com.samourai.whirlpool.server.services.MinerFeeService;
import com.samourai.whirlpool.server.services.MixSorobanService;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import com.samourai.whirlpool.server.services.soroban.SorobanCoordinatorService;
import com.samourai.whirlpool.server.utils.Utils;
import com.samourai.xmanager.protocol.XManagerService;
import com.samourai.xmanager.protocol.rest.AddressIndexResponse;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@SpringBootApplication
@ServletComponentScan(value = "com.samourai.whirlpool.server.config.filters")
public class Application extends ServerApplication {
  private static final Logger log = LoggerFactory.getLogger(Application.class);

  @Autowired private RpcClientService rpcClientService;

  @Autowired private ExportService exportService;

  @Autowired private WhirlpoolServerConfig serverConfig;

  @Autowired private XManagerClient xManagerClient;

  @Autowired private BackendService backendService;

  @Autowired private MinerFeeService minerFeeService;

  @Autowired private SorobanCoordinatorService sorobanCoordinatorService;

  // required to launch on startup
  @Autowired private MixSorobanService mixSorobanService;

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Override
  public void runServer() throws Exception {
    // check RPC connectivity
    if (!rpcClientService.testConnectivity()) {
      throw new Exception("RPC connexion failed");
    }

    // check XM connectivity
    AddressIndexResponse addressIndexResponse =
        xManagerClient.getAddressIndexOrDefault(XManagerService.XM000);
    log.info("XM index: " + addressIndexResponse.index);

    // check backend connectivity
    MinerFee minerFee = backendService.fetchMinerFee();
    log.info("Backend minerFee: " + minerFee._getMap());

    // log activity
    ActivityCsv activityCsv = new ActivityCsv("STARTUP", null, null, null, null, null);
    exportService.exportActivity(activityCsv);

    // server starting...
  }

  @Override
  protected ServerConfig getServerConfig() {
    return serverConfig;
  }

  @Override
  protected void setLoggerDebug() {
    Utils.setLoggerDebug();
  }

  @PreDestroy
  public void preDestroy() {
    log.warn("********** Shutting down **********");
    sorobanCoordinatorService.stop();
    minerFeeService.stop();
  }
}
