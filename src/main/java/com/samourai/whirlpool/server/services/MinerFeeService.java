package com.samourai.whirlpool.server.services;

import com.samourai.wallet.api.backend.MinerFeeTarget;
import com.samourai.whirlpool.client.wallet.data.minerFee.BasicMinerFeeSupplier;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.orchestrators.MinerFeeOrchestrator;
import org.springframework.stereotype.Service;

@Service
public class MinerFeeService {
  private BasicMinerFeeSupplier minerFeeSupplier;
  private MinerFeeOrchestrator minerFeeOrchestrator;

  public MinerFeeService(WhirlpoolServerConfig serverConfig, BackendService backendService) {
    WhirlpoolServerConfig.MinerFeePerBConfig feePerBConfig = serverConfig.getFeePerB();
    this.minerFeeSupplier =
        new BasicMinerFeeSupplier(
            feePerBConfig.getMin(), feePerBConfig.getMax(), feePerBConfig.getFallback());
    this.minerFeeOrchestrator = new MinerFeeOrchestrator(backendService, minerFeeSupplier);
    this.minerFeeOrchestrator.start(true);
  }

  public int getMixFeePerB() {
    return minerFeeSupplier.getFee(MinerFeeTarget.BLOCKS_24);
  }

  public void stop() {
    minerFeeOrchestrator.stop();
  }
}
