package com.samourai.whirlpool.server.controllers.websocket;

import com.google.common.collect.ImmutableMap;
import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.v0.WhirlpoolProtocolV0;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.services.ExportService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.services.WSMessageService;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public abstract class AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WSMessageService WSMessageService;
  private ExportService exportService;

  public AbstractWebSocketController(
      WSMessageService WSMessageService, ExportService exportService) {
    this.WSMessageService = WSMessageService;
    this.exportService = exportService;
  }

  protected void validateHeaders(StompHeaderAccessor headers) throws Exception {
    String clientProtocolVersion =
        headers.getFirstNativeHeader(WhirlpoolProtocolV0.HEADER_PROTOCOL_VERSION);
    if (!WhirlpoolProtocolV0.PROTOCOL_VERSION.equals(clientProtocolVersion)) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.VERSION_MISMATCH,
          "Version mismatch: server="
              + WhirlpoolProtocolV0.PROTOCOL_VERSION
              + ", client="
              + (clientProtocolVersion != null ? clientProtocolVersion : "unknown"));
    }
  }

  private boolean noStackTrace(Exception e) {
    return NotifiableException.class.isAssignableFrom(e.getClass());
  }

  protected void handleException(
      Exception e,
      Principal principal,
      SimpMessageHeaderAccessor messageHeaderAccessor,
      String activity) {

    NotifiableException notifiable = NotifiableException.computeNotifiableException(e);
    int errorCode = notifiable.getErrorCode();
    String message = notifiable.getMessage();
    String username = principal.getName();
    WSMessageService.sendPrivateError(username, errorCode, message);

    // skip healthCheck
    if (!RegisterInputService.HEALTH_CHECK_SUCCESS.equals(message)) {
      if (noStackTrace(e)) {
        // already logged by WSMessageService
      } else {
        log.error("handleException", e);
      }

      // log activity
      Map<String, String> clientDetails = computeClientDetails(messageHeaderAccessor);
      clientDetails.put("u", username);
      Map<String, String> details = ImmutableMap.of("error", message);
      Boolean tor = Utils.getTor(messageHeaderAccessor);
      ActivityCsv activityCsv = new ActivityCsv(activity, null, details, tor, clientDetails);
      getExportService().exportActivity(activityCsv);
    }
  }

  protected Map<String, String> computeClientDetails(
      SimpMessageHeaderAccessor messageHeaderAccessor) {
    Map<String, List<String>> nativeHeaders =
        (Map) messageHeaderAccessor.getHeader("nativeHeaders");
    if (nativeHeaders == null) {
      return null;
    }
    String[] ignoreHeaders =
        new String[] {"content-type", "content-length", "destination", "protocolVersion"};

    Map<String, String> clientDetails = new LinkedHashMap<>();
    nativeHeaders.entrySet().stream()
        .filter(e -> !ArrayUtils.contains(ignoreHeaders, e.getKey()))
        .forEach(
            e -> {
              String value =
                  e.getValue().size() == 1 ? e.getValue().get(0) : e.getValue().toString();
              clientDetails.put(e.getKey(), value);
            });
    return clientDetails;
  }

  protected WSMessageService getWSMessageService() {
    return WSMessageService;
  }

  protected ExportService getExportService() {
    return exportService;
  }
}
