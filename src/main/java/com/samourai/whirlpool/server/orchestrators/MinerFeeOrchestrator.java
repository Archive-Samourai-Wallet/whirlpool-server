package com.samourai.whirlpool.server.orchestrators;

import com.samourai.wallet.api.backend.MinerFee;
import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.whirlpool.client.wallet.data.minerFee.BasicMinerFeeSupplier;
import com.samourai.whirlpool.server.services.BackendService;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinerFeeOrchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final int LOOP_DELAY = 300000; // 5min

  private BackendService backendService;
  private BasicMinerFeeSupplier minerFeeSupplier;

  public MinerFeeOrchestrator(
      BackendService backendService, BasicMinerFeeSupplier minerFeeSupplier) {
    super(LOOP_DELAY, 0, null);
    this.backendService = backendService;
    this.minerFeeSupplier = minerFeeSupplier;
  }

  @Override
  protected void runOrchestrator() {
    try {
      // fetch minerFee from backend
      MinerFee minerFee = backendService.fetchMinerFee();
      minerFeeSupplier.setValue(minerFee);
      if (log.isDebugEnabled()) {
        log.debug("minerFeeSupplier.setValue: " + minerFee._getMap());
      }
    } catch (Exception e) {
      log.error("Failed to fetch minerFee", e);
    }
  }
}
