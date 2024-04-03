package com.samourai.whirlpool.server.utils;

import ch.qos.logback.classic.Level;
import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.javaserver.utils.LogbackUtils;
import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.javawsserver.interceptors.JWSSIpHandshakeInterceptor;
import com.samourai.soroban.protocol.payload.SorobanErrorMessage;
import com.samourai.wallet.bipFormat.BIP_FORMAT;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.httpClient.HttpUsage;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.SecretWalletContext;
import com.samourai.whirlpool.server.beans.TxOutSignature;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.services.rpc.JSONRpcClientServiceImpl;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

public class Utils {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final ServerUtils serverUtils = ServerUtils.getInstance();

  private static int BTC_TO_SATOSHIS = 100000000;
  private static final String IP_TOR = "127.0.0.1";

  public static final HttpUsage HTTPUSAGE_SOROBAN_ONION = new HttpUsage("SOROBAN_ONION");

  public static Boolean getTor(SimpMessageHeaderAccessor messageHeaderAccessor) {
    String ip = JWSSIpHandshakeInterceptor.getIp(messageHeaderAccessor);
    return getTor(ip);
  }

  public static Boolean getTor(HttpServletRequest request) {
    String ip = request != null ? request.getRemoteAddr() : null;
    return getTor(ip);
  }

  private static Boolean getTor(String ip) {
    return ip != null ? IP_TOR.equals(ip) : null;
  }

  public static String getRandomString(int length) {
    RandomStringGenerator randomStringGenerator =
        new RandomStringGenerator.Builder()
            .filteredBy(CharacterPredicates.ASCII_ALPHA_NUMERALS)
            .build();
    return randomStringGenerator.generate(length);
  }

  public static String generateUniqueString() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  public static TransactionWitness witnessUnserialize64(String[] witnesses64) {
    TransactionWitness witness = new TransactionWitness(witnesses64.length);
    for (int i = 0; i < witnesses64.length; i++) {
      String witness64 = witnesses64[i];
      byte[] witnessItem = WhirlpoolProtocol.decodeBytes(witness64);
      witness.setPush(i, witnessItem);
    }
    return witness;
  }

  public static <K, V extends Comparable<? super V>> Map<K, V> sortMapByValue(Map<K, V> map) {
    Comparator<Map.Entry<K, V>> comparator = Map.Entry.comparingByValue();
    Map<K, V> sortedMap =
        map.entrySet().stream()
            .sorted(comparator)
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    return sortedMap;
  }

  public static String computeOutpointId(TxOutPoint outPoint) {
    return computeOutpointId(outPoint.getHash(), outPoint.getIndex());
  }

  public static String computeOutpointId(String utxoHash, long utxoIndex) {
    return utxoHash + ":" + utxoIndex;
  }

  public static String computeInputId(RegisteredInput registeredInput) {
    TxOutPoint outPoint = registeredInput.getOutPoint();
    return computeInputId(outPoint.getHash(), outPoint.getIndex(), registeredInput.getUsername());
  }

  public static String computeInputId(String utxoHash, long utxoIndex, String username) {
    return utxoHash + ":" + utxoIndex + "_" + username;
  }

  public static <T> Page<T> paginateList(final Pageable pageable, List<T> list) {
    int first = Math.min(new Long(pageable.getOffset()).intValue(), list.size());
    int last = Math.min(first + pageable.getPageSize(), list.size());
    return new PageImpl<>(list.subList(first, last), pageable, list.size());
  }

  public static void setLoggerDebug() {
    serverUtils.setLoggerDebug("com.samourai.whirlpool");
    serverUtils.setLoggerDebug("com.samourai.wallet");
    serverUtils.setLoggerDebug("com.samourai.soroban.client");

    // skip noisy logs
    LogbackUtils.setLogLevel(
        "org.springframework.web.socket.config.WebSocketMessageBrokerStats",
        org.slf4j.event.Level.ERROR.toString());
    LogbackUtils.setLogLevel(
        "com.samourai.javawsserver.config", org.slf4j.event.Level.INFO.toString());
  }

  public static BigDecimal satoshisToBtc(long satoshis) {
    return new BigDecimal(satoshis).divide(new BigDecimal(BTC_TO_SATOSHIS));
  }

  public static void testJsonRpcClientConnectivity(RpcClientService rpcClientService)
      throws Exception {
    // connect to rpc node
    if (!JSONRpcClientServiceImpl.class.isAssignableFrom(rpcClientService.getClass())) {
      throw new Exception("Expected rpcClient of type " + JSONRpcClientServiceImpl.class.getName());
    }
    if (!rpcClientService.testConnectivity()) {
      throw new Exception("rpcClient couldn't connect to bitcoin node");
    }
  }

  public static String getToAddressBech32(
      TransactionOutput out, Bech32UtilGeneric bech32Util, NetworkParameters params) {
    Script script = out.getScriptPubKey();
    if (script.isOpReturn()) {
      return null;
    }
    if (script.isSentToP2WPKH() || script.isSentToP2WSH()) {
      try {
        return bech32Util.getAddressFromScript(script, params);
      } catch (Exception e) {
        log.error("toAddress failed for bech32", e);
      }
    }
    try {
      return script.getToAddress(params).toBase58();
    } catch (Exception e) {
      log.error("unable to find toAddress", e);
    }
    return null;
  }

  public static String obfuscateString(String str, int offset) {
    if (str == null || str.length() <= offset) {
      return str;
    }
    return str.substring(0, offset) + "***" + str.substring(str.length() - offset, str.length());
  }

  public static String computeBlameIdentitifer(RegisteredInput registeredInput) {
    TxOutPoint txOutPoint = registeredInput.getOutPoint();

    String utxoHash = txOutPoint.getHash().trim().toLowerCase();
    long utxoIndex = txOutPoint.getIndex();
    boolean liquidity = registeredInput.isLiquidity();
    return computeBlameIdentitifer(utxoHash, utxoIndex, liquidity);
  }

  public static String computeBlameIdentitifer(String utxoHash, long utxoIndex, boolean liquidity) {
    if (!liquidity) {
      // comes from TX0 => ban whole TX0
      return utxoHash;
    }

    // comes from previous mix => ban UTXO
    String utxo = Utils.computeOutpointId(utxoHash, utxoIndex);
    return utxo;
  }

  protected static byte[] serializeTransactionOutput(
      String address, long value, NetworkParameters params) throws Exception {
    TransactionOutput txOut = BIP_FORMAT.PROVIDER.getTransactionOutput(address, value, params);
    return txOut.bitcoinSerialize();
  }

  public static TxOutSignature signTransactionOutput(
      String feeAddress,
      long feeValue,
      NetworkParameters params,
      SecretWalletContext secretWalletContext)
      throws Exception {
    ECKey signingKey = secretWalletContext.getAddress().getECKey();
    return signTransactionOutput(feeAddress, feeValue, params, signingKey);
  }

  public static TxOutSignature signTransactionOutput(
      String feeAddress, long feeValue, NetworkParameters params, ECKey ecKey) throws Exception {
    String signingAddress = BIP_FORMAT.LEGACY.getToAddress(ecKey, params);
    byte[] feeOutputSerialized = serializeTransactionOutput(feeAddress, feeValue, params);
    Sha256Hash preHash =
        Sha256Hash.twiceOf(ECKeyUtils.formatMessageForSigning(feeOutputSerialized));
    String signature = ECKeyUtils.signMessage(ecKey, preHash);
    return new TxOutSignature(signingAddress, preHash.toString(), signature);
  }

  public static String signMessage(SecretWalletContext secretWalletContext, String payload)
      throws Exception {
    HD_Address signingAddress = secretWalletContext.getAddress();
    if (log.isDebugEnabled()) {
      log.debug("signing address: " + signingAddress.getAddressString());
    }
    return MessageSignUtilGeneric.getInstance().signMessage(signingAddress.getECKey(), payload);
  }

  public static Logger prefixLogger(Logger log, String logPrefix) {
    // Level level = ((ch.qos.logback.classic.Logger) log).getEffectiveLevel();
    // TODO not working with setLoggerDebug?
    Level level = Level.DEBUG;
    Logger newLog = LoggerFactory.getLogger(log.getName() + "[" + logPrefix + "]");
    ((ch.qos.logback.classic.Logger) newLog).setLevel(level);
    return newLog;
  }

  public static SorobanErrorMessage computeSorobanErrorMessage(Exception e) {
    int errorCode;
    String message;
    if (e instanceof IllegalInputException) {
      errorCode = ((IllegalInputException) e).getErrorCode();
      message = e.getMessage();
      log.warn(
          "SOROBAN_REPLY_ERROR ILLEGAL_INPUT -> "
              + message
              + " "
              + ((IllegalInputException) e).getInputInfo());
    } else if (e instanceof NotifiableException) {
      errorCode = ((NotifiableException) e).getErrorCode();
      message = e.getMessage();
      log.warn("SOROBAN_REPLY_ERROR NOTIFIABLE -> " + message);
    } else {
      errorCode = WhirlpoolErrorCode.SERVER_ERROR;
      message = NotifiableException.computeNotifiableException(e).getMessage();
      log.error("SOROBAN_REPLY_ERROR SERVER_ERROR -> " + message, e);
    }
    return new SorobanErrorMessage(errorCode, message);
  }
}
