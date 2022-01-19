package com.samourai.whirlpool.server.services;

import com.google.common.collect.ImmutableMap;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.persistence.to.BanTO;
import com.samourai.whirlpool.server.persistence.to.BlameTO;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class BanService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private DbService dbService;
  private WhirlpoolServerConfig serverConfig;
  private ExportService exportService;
  private MetricService metricService;

  @Autowired
  public BanService(
      DbService dbService,
      WhirlpoolServerConfig serverConfig,
      ExportService exportService,
      MetricService metricService) {
    this.dbService = dbService;
    this.serverConfig = serverConfig;
    this.exportService = exportService;
    this.metricService = metricService;
  }

  public BanTO banTemporary(String identifier, String response, String notes) {
    Timestamp created = new Timestamp(System.currentTimeMillis());
    return banTemporary(created, identifier, response, notes);
  }

  protected BanTO banTemporary(
      Timestamp created, String identifier, String response, String notes) {
    // find last ban if any
    Optional<BanTO> lastBan = dbService.findBanByIdentifierLast(identifier);

    long expirationDelay = serverConfig.getBan().getExpiration() * 1000;
    if (lastBan.isPresent()) {
      // enforce recidivismFactor
      long lastBanDuration = lastBan.get().getDuration();
      expirationDelay = lastBanDuration * serverConfig.getBan().getRecidivismFactor();
      if (log.isDebugEnabled()) {
        log.debug(
            "ban recidivism "
                + identifier
                + ": lastBanDuration="
                + (lastBanDuration / 1000 / 60)
                + "min -> "
                + (expirationDelay / 1000 / 60)
                + "min");
      }
    }
    return banTemporary(created, identifier, response, notes, expirationDelay);
  }

  protected BanTO banTemporary(
      Timestamp created, String identifier, String response, String notes, long expirationDelay) {
    Timestamp expiration = new Timestamp(created.getTime() + expirationDelay);
    return dbService.saveBan(created, identifier, expiration, response, notes);
  }

  public Optional<BanTO> findActiveBan(String utxoHash, long utxoIndex) {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    return findActiveBan(utxoHash, utxoIndex, now);
  }

  protected Optional<BanTO> findActiveBan(String utxoHash, long utxoIndex, Timestamp now) {
    // find ban for hash
    String identifierTx0 = Utils.computeBlameIdentitifer(utxoHash, utxoIndex, false);
    Optional<BanTO> banByTx0 = findActiveBan(identifierTx0, now);
    if (banByTx0.isPresent()) {
      // ban found for hash => this is a banned TX0
      return banByTx0;
    }

    //  find ban  for  utxo
    String identifierUtxo = Utils.computeBlameIdentitifer(utxoHash, utxoIndex, true);
    Optional<BanTO> banByUtxo = findActiveBan(identifierUtxo, now);
    return banByUtxo;
  }

  protected Optional<BanTO> findActiveBan(String identifier, Timestamp now) {
    List<BanTO> activeBans = dbService.findBanByIdentifierAndExpirationAfterOrNull(identifier, now);
    return (!activeBans.isEmpty() ? Optional.of(activeBans.get(0)) : Optional.empty());
  }

  public Page<BanTO> findActiveBans(Pageable pageable) {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    Page<BanTO> bans = dbService.findBanByExpirationAfterOrNull(now, pageable);
    return bans;
  }

  public void onBlame(RegisteredInput registeredInput, String identifier, List<BlameTO> blames) {
    int maxBlames = serverConfig.getBan().getBlames();

    long blamePeriodMs = serverConfig.getBan().getPeriod() * 1000;
    Timestamp blameCreatedAfter = new Timestamp(System.currentTimeMillis() - blamePeriodMs);

    // ignore expired blames
    List<BlameTO> activeBlames =
        blames.stream()
            .filter(blameTO -> blameTO.getCreated().after(blameCreatedAfter))
            .collect(Collectors.toList());
    if (log.isDebugEnabled()) {
      int i = 0;
      for (BlameTO b : activeBlames) {
        log.warn("- blame " + i + "/" + maxBlames + ": " + b);
        i++;
      }
    }

    int countActiveBlames = activeBlames.size();
    int blamesRemainingBeforeBan = maxBlames - countActiveBlames;
    if (blamesRemainingBeforeBan > 0) {
      // not banned yet
      log.info(
          countActiveBlames
              + " active blames found for "
              + identifier
              + " -> "
              + blamesRemainingBeforeBan
              + " blames remaining before ban");
      return;
    }

    // ban now
    log.warn(countActiveBlames + " active blames found for " + identifier + " -> ban");
    long blamePeriodMinutes = blamePeriodMs / 1000 / 60;
    String blameReasons =
        String.join(
            ", ",
            activeBlames.stream()
                .map(blameTO -> blameTO.getReason().name())
                .collect(Collectors.toList()));
    String reason = countActiveBlames + " blames in " + blamePeriodMinutes + "min: " + blameReasons;
    banTemporary(identifier, null, reason);

    // log activity
    ActivityCsv activityCsv =
        new ActivityCsv(
            "BAN",
            registeredInput.getPoolId(),
            registeredInput,
            ImmutableMap.of("reason", reason),
            null);
    exportService.exportActivity(activityCsv);
    metricService.onBan(registeredInput);
  }
}
