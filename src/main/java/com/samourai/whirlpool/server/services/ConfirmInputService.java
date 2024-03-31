package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.server.beans.FailMode;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
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
  private CryptoService cryptoService;
  private WhirlpoolServerConfig serverConfig;

  @Autowired
  public ConfirmInputService(
      MixService mixService,
      PoolService poolService,
      ExportService exportService,
      CryptoService cryptoService,
      WhirlpoolServerConfig serverConfig) {
    this.mixService = mixService;
    this.poolService = poolService;
    this.exportService = exportService;
    this.cryptoService = cryptoService;
    this.serverConfig = serverConfig;
  }

  public synchronized Optional<byte[]> confirmInput_webSocket(
      String mixId, byte[] blindedBordereau, String userHash, String username)
      throws NotifiableException {
    try {
      Mix mix = mixService.getMix(mixId);
      // old non-soroban clients
      RegisteredInput registeredInput =
          mix.removeConfirmingInputByUsername(username)
              .orElseThrow(
                  () ->
                      new IllegalInputException(
                          WhirlpoolErrorCode.SERVER_ERROR,
                          "Confirming input not found",
                          "username=" + username));
      try {
        return Optional.of(confirmInput(mix, registeredInput, blindedBordereau, userHash));
      } catch (QueueInputException e) {
        // confirmInput rejected => requeue classic input which stays connected to websocket
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
        String poolId = registeredInput.getPoolId();
        // log activity
        ActivityCsv activityCsv =
            new ActivityCsv("CONFIRM_INPUT:QUEUED", poolId, registeredInput, null, null);
        exportService.exportActivity(activityCsv);

        poolService.registerInput(registeredInput, null);
        return Optional.empty();
      }
    } catch (MixException e) {
      // ConfirmInput too late, mix already started => input was already silently requeued
      return Optional.empty();
    } catch (Exception e) {
      // Soroban network error?
      throw NotifiableException.computeNotifiableException(e);
    }
  }

  public synchronized byte[] confirmInput(
      Mix mix, RegisteredInput registeredInput, byte[] blindedBordereau, String userHash)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) INPUT_CONFIRM " + mix.getMixId() + " " + registeredInput.toString());
    }

    // failMode
    serverConfig.checkFailMode(FailMode.CONFIRM_INPUT);

    // set lastUserHash
    registeredInput.setLastUserHash(userHash);

    // last input validations (after setting lastUserHash)
    mixService.validateOnConfirmInput(mix, registeredInput);

    // sign bordereau to reply
    byte[] signedBordereau = cryptoService.signBlindedOutput(blindedBordereau, mix.getKeyPair());

    // add to mix inputs
    mix.registerInput(registeredInput, signedBordereau);
    mixService.logMixStatus(mix);

    // log activity
    ActivityCsv activityCsv =
        new ActivityCsv("CONFIRM_INPUT", mix.getPool().getPoolId(), registeredInput, null, null);
    exportService.exportActivity(activityCsv);

    // manage surges when enough mustMix confirmed
    if (!registeredInput.isLiquidity() && mix.hasMinMustMixAndFeeReached()) {
      // enough mustMix confirmed, update mix surge limit
      mix.setSurge();
      // surges will be invited soon by mixLimitsService
    }
    return signedBordereau;
  }
}
