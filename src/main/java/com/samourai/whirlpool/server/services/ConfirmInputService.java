package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import com.samourai.whirlpool.server.exceptions.ServerErrorCode;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConfirmInputService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MixService mixService;
  private PoolService poolService;
  private ExportService exportService;

  @Autowired
  public ConfirmInputService(
      MixService mixService, PoolService poolService, ExportService exportService) {
    this.mixService = mixService;
    this.poolService = poolService;
    this.exportService = exportService;
  }

  public synchronized Optional<byte[]> confirmInputOrQueuePool(
      String mixId,
      String username,
      byte[] blindedBordereau,
      String userHash,
      String utxoHashOrNull, // null for old non-soroban clients
      Long utxoIndexOrNull) // null for old non-soroban clients
      throws NotifiableException {
    try {
      // add input to mix & reply confirmInputResponse
      return Optional.of(
          mixService.confirmInput(
              mixId, username, blindedBordereau, userHash, utxoHashOrNull, utxoIndexOrNull));
    } catch (QueueInputException e) {
      // confirmInput rejected
      boolean isSoroban = utxoHashOrNull != null;
      if (!isSoroban) {
        // requeue classic input which stays connected to websocket
        if (log.isDebugEnabled()) {
          log.debug(
              "["
                  + e.getPoolId()
                  + "/"
                  + mixId
                  + "] Input queued: "
                  + e.getRegisteredInput().getOutPoint()
                  + ", reason="
                  + e.getMessage());
        }
        queueOnConfirmInputClassicRejected(e.getRegisteredInput(), userHash);
        return Optional.empty();
      } else {
        // disconnect Soroban input
        throw new NotifiableException(ServerErrorCode.INPUT_REJECTED, e.getMessage());
      }
    } catch (MixException e) {
      // ConfirmInput too late, mix already started => input was already silently requeued
      return Optional.empty();
    }
  }

  private void queueOnConfirmInputClassicRejected(RegisteredInput registeredInput, String userHash)
      throws NotifiableException {
    String poolId = registeredInput.getPoolId();
    // log activity
    ActivityCsv activityCsv =
        new ActivityCsv("CONFIRM_INPUT:QUEUED", poolId, registeredInput, null, null);
    exportService.exportActivity(activityCsv);

    poolService.registerInput(
        poolId,
        registeredInput.getUsername(),
        registeredInput.isLiquidity(),
        registeredInput.getOutPoint(),
        registeredInput.getTor(),
        registeredInput.getSorobanPaymentCode(),
        registeredInput.getSorobanInitialPayload(),
        userHash);
  }
}
