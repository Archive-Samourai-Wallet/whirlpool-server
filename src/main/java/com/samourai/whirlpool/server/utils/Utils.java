package com.samourai.whirlpool.server.utils;

import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.services.rpc.JSONRpcClientServiceImpl;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final SecureRandom secureRandom = new SecureRandom();
  private static final ServerUtils serverUtils = ServerUtils.getInstance();

  private static int BTC_TO_SATOSHIS = 100000000;

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

  public static String getRawTx(Transaction tx) {
    return org.bitcoinj.core.Utils.HEX.encode(tx.bitcoinSerialize());
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

  public static <K, V> Map.Entry<K, V> getRandomEntry(Map<K, V> map) {
    if (map.isEmpty()) {
      return null;
    }
    Object entries[] = map.entrySet().toArray();
    return (Map.Entry<K, V>) entries[secureRandom.nextInt(entries.length)];
  }

  public static <T> T getRandomEntry(List<T> list) {
    if (list.isEmpty()) {
      return null;
    }
    return list.get(secureRandom.nextInt(list.size()));
  }

  public static String computeInputId(TxOutPoint outPoint) {
    return computeInputId(outPoint.getHash(), outPoint.getIndex());
  }

  public static String computeInputId(String utxoHash, long utxoIndex) {
    return utxoHash + ":" + utxoIndex;
  }

  public static void setLoggerDebug() {
    serverUtils.setLoggerDebug("com.samourai.whirlpool");
    serverUtils.setLoggerDebug("com.samourai.wallet");
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
    String utxo = Utils.computeInputId(utxoHash, utxoIndex);
    return utxo;
  }
}
