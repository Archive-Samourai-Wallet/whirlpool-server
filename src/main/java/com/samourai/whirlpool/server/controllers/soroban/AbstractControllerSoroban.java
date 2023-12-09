package com.samourai.whirlpool.server.controllers.soroban;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.soroban.client.PayloadWithSender;
import com.samourai.soroban.client.SorobanPayload;
import com.samourai.soroban.client.dialog.SorobanErrorMessage;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.bip47.rpc.Bip47Partner;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.soroban.api.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.protocol.soroban.api.WhirlpoolPartnerApiCoordinator;
import com.samourai.whirlpool.server.config.WhirlpoolServerContext;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractControllerSoroban<T> extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  protected static final int LOOP_DELAY_SLOW = 15000; // 15s
  protected static final int LOOP_DELAY_FAST = 2000; // 2s

  protected final AsyncUtil asyncUtil = AsyncUtil.getInstance();
  protected final RpcSession rpcSession;
  protected final WhirlpoolServerContext serverContext;
  protected WhirlpoolApiCoordinator whirlpoolApiCoordinator;
  // 30m, enough to avoid reprocessing same payload before Soroban forgets it, should be >
  // LOOP_DELAY
  private long expirationMs = 1800000;

  protected final String logId;

  private Map<String, Long> processedByKey; // process time by key

  public AbstractControllerSoroban(
      int LOOP_DELAY,
      String logId,
      WhirlpoolServerContext serverContext,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator) {
    super(LOOP_DELAY, 0, null);
    this.logId = logId;
    this.rpcSession = serverContext.getRpcSession();
    this.serverContext = serverContext;
    this.whirlpoolApiCoordinator = whirlpoolApiCoordinator;
    logDebug("starting...");
  }

  @Override
  protected String getThreadName() {
    return super.getThreadName() + logId;
  }

  protected void setExpirationMs(long expirationMs) {
    this.expirationMs = expirationMs;
  }

  protected void logDebug(String msg) {
    if (log.isDebugEnabled()) {
      log.debug("[" + logId + "] " + msg);
    }
  }

  protected abstract Collection<T> fetch() throws Exception;

  protected abstract void process(T message, String key) throws Exception;

  protected void onExisting(T message, String key) throws Exception {}

  protected void onExpiring(String key) throws Exception {}

  protected abstract String computeKey(T message);

  @Override
  protected void runOrchestrator() {
    int processeds = 0;
    int existings = 0;
    long now = System.currentTimeMillis();
    try {
      Collection<T> messages = fetch();
      for (T message : messages) {
        try {
          String key = computeKey(message);
          if (this.processedByKey.containsKey(key)) {
            existings++;
            try {
              onExisting(message, key);
            } catch (Exception e) {
              log.error("[" + logId + "] Error on existing message:", e);
            }
          } else {
            processeds++;
            processedByKey.put(key, now);
            try {
              logDebug("Processing: " + key);
              process(message, key);
            } catch (Exception e) {
              log.error("[" + logId + "] Error processing a message:", e);
            }
          }
        } catch (Exception e) {
          log.error("", e);
        }
      }
    } catch (Exception e) {
      log.error("[" + logId + "] Failed to fetch from Soroban", e);
    }

    // clean expired inputs
    int expiredInputs = cleanup();
    if (processeds > 0 || expiredInputs > 0) {
      logDebug(
          processeds + " processeds, " + existings + " existings, " + expiredInputs + " expired");
    }
  }

  private synchronized int cleanup() {
    long minProcessTime = System.currentTimeMillis() - expirationMs;
    // cleanup expired
    Set<String> expiredKeys =
        processedByKey.entrySet().stream()
            .filter(e -> e.getValue() < minProcessTime)
            .map(e -> e.getKey())
            .collect(Collectors.toSet()); // required to avoid ConcurrentModificationException
    expiredKeys.forEach(
        key -> {
          try {
            onExpiring(key);
          } catch (Exception e) {
            log.error("[" + logId + "] Error on expiring message:", e);
          }
          processedByKey.remove(key);
        });
    return expiredKeys.size();
  }

  @Override
  protected void resetOrchestrator() {
    super.resetOrchestrator();

    processedByKey = new LinkedHashMap<>();
  }

  @Override
  public synchronized void stop() {
    super.stop();
    logDebug("stop");
    processedByKey.clear();
  }

  protected Bip47Partner getBip47Partner(PaymentCode sender) throws Exception {
    return rpcSession.getRpcWallet().getBip47Partner(sender, false);
  }

  protected WhirlpoolPartnerApiCoordinator getWhirlpoolPartnerApiCoordinator(
      Bip47Partner bip47Partner) {
    return serverContext.getWhirlpoolPartnerApi(bip47Partner);
  }

  protected void sendReplyToRequest(PayloadWithSender request, SorobanPayload response)
      throws Exception {
    // reply to request
    Bip47Partner bip47Partner = getBip47Partner(request.getSender());
    String requestId = whirlpoolApiCoordinator.getRequestId(request.getPayload().toPayload());
    getWhirlpoolPartnerApiCoordinator(bip47Partner)
        .sendReplyEncrypted(response, requestId)
        .subscribe();
  }

  protected void sendError(PayloadWithSender request, Exception e) throws Exception {
    int errorCode;
    String message;
    if (e instanceof IllegalInputException) {
      errorCode = ((IllegalInputException) e).getErrorCode();
      message = e.getMessage();
    } else {
      errorCode = WhirlpoolErrorCode.SERVER_ERROR;
      message = NotifiableException.computeNotifiableException(e).getMessage();
    }
    SorobanErrorMessage sorobanErrorMessage = new SorobanErrorMessage(errorCode, message);
    sendReplyToRequest(request, sorobanErrorMessage);
  }

  // for tests
  public void _runOrchestrator() {
    runOrchestrator();
  }
}
