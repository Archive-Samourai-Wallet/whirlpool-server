package com.samourai.whirlpool.server.beans;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.util.RandomUtil;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputPool {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private Map<String, RegisteredInput> inputsById;

  public InputPool() {
    this.inputsById = new ConcurrentHashMap<>();
  }

  public void register(RegisteredInput registeredInput) {
    // overwrite if it was already registered
    String inputId = Utils.computeInputId(registeredInput.getOutPoint());
    inputsById.put(inputId, registeredInput);
  }

  public Optional<RegisteredInput> findByUsername(String username) {
    return inputsById
        .values()
        .parallelStream()
        .filter(registeredInput -> username.equals(registeredInput.getUsername()))
        .findFirst();
  }

  public Optional<RegisteredInput> findByUtxo(TxOutPoint outPoint) {
    return findByUtxo(outPoint.getHash(), outPoint.getIndex());
  }

  public Optional<RegisteredInput> findByUtxo(String utxoHash, long utxoIndex) {
    String key = Utils.computeInputId(utxoHash, utxoIndex);
    return Optional.ofNullable(inputsById.get(key));
  }

  public Optional<RegisteredInput> findBySorobanSender(PaymentCode sender) {
    String pCode = sender.toString();
    return inputsById.values().stream()
        .filter(
            confirmedInput ->
                confirmedInput.getSorobanInput() != null
                    && confirmedInput
                        .getSorobanInput()
                        .getBip47Partner()
                        .getPaymentCodePartner()
                        .toString()
                        .equals(pCode))
        .findFirst();
  }

  public Optional<RegisteredInput> findByAddress(String address) {
    String addressToLower = address.toLowerCase();
    return inputsById.values().stream()
        .filter(
            confirmedInput ->
                addressToLower.equals(confirmedInput.getOutPoint().getToAddress().toLowerCase()))
        .findFirst();
  }

  public Collection<RegisteredInput> findByQuarantine(boolean quarantine) {
    return inputsById
        .values()
        .parallelStream()
        .filter(registeredInput -> registeredInput.isQuarantine() == quarantine)
        .collect(Collectors.toList());
  }

  public Collection<String> getQuarantineDetails() {
    return findByQuarantine(true).stream()
        .map(input -> input.getOutPoint().toKey() + ": " + input.getQuarantineReason())
        .collect(Collectors.toList());
  }

  public synchronized Optional<RegisteredInput> removeRandom(
      Predicate<Map.Entry<String, RegisteredInput>> filter) {
    List<String> eligibleInputIds =
        inputsById
            .entrySet()
            .parallelStream()
            .filter(filter)
            .map(entry -> entry.getKey())
            .collect(Collectors.toList());
    return removeRandom(eligibleInputIds);
  }

  private synchronized Optional<RegisteredInput> removeRandom(List<String> eligibleInputIds) {
    if (!eligibleInputIds.isEmpty()) {
      String randomInputId = RandomUtil.getInstance().next(eligibleInputIds);
      RegisteredInput registeredInput = inputsById.remove(randomInputId);
      return Optional.of(registeredInput);
    }
    return Optional.empty();
  }

  public synchronized Optional<RegisteredInput> removeByUsername(String username) {
    return removeBy(findByUsername(username));
  }

  public synchronized Optional<RegisteredInput> removeByUtxo(String utxoHash, long utxoIndex) {
    return removeBy(findByUtxo(utxoHash, utxoIndex));
  }

  public synchronized Optional<RegisteredInput> removeBySorobanSender(PaymentCode sender) {
    return removeBy(findBySorobanSender(sender));
  }

  protected Optional<RegisteredInput> removeBy(Optional<RegisteredInput> input) {
    if (input.isPresent()) {
      String inputId = Utils.computeInputId(input.get().getOutPoint());
      inputsById.remove(inputId);
    }
    return input;
  }

  public synchronized Optional<RegisteredInput> removeByUtxo(TxOutPoint txOut) {
    return removeByUtxo(txOut.getHash(), txOut.getIndex());
  }

  public synchronized Collection<RegisteredInput> clear() {
    Collection<RegisteredInput> inputs =
        new LinkedList<>(inputsById.values()); // copy to avoid getting cleared next!
    inputsById.clear();
    return inputs;
  }

  public synchronized void clearQuarantine() {
    findByQuarantine(true).forEach(input -> input.clearQuarantine());
  }

  public void resetLastUserHash() {
    inputsById.values().forEach(registedInput -> registedInput.setLastUserHash(null));
  }

  // ------------

  public boolean hasInput(TxOutPoint outPoint) {
    return findByUtxo(outPoint.getHash(), outPoint.getIndex()).isPresent();
  }

  public boolean hasInputs() {
    return !inputsById.isEmpty();
  }

  public int getSize() {
    return inputsById.size();
  }

  public int getSizeByTor(boolean tor) {
    return (int)
        inputsById
            .values()
            .parallelStream()
            .filter(input -> Boolean.valueOf(tor).equals(input.getTor()))
            .count();
  }

  public int getSizeBySoroban(boolean soroban) {
    return getListBySoroban(soroban).size();
  }

  public Collection<RegisteredInput> getListBySoroban(boolean soroban) {
    return inputsById
        .values()
        .parallelStream()
        .filter(input -> soroban == input.isSoroban())
        .collect(Collectors.toList());
  }

  public int getSizeByLiquidity(boolean liquidity) {
    return getListByLiquidity(liquidity).size();
  }

  public Collection<RegisteredInput> getListByLiquidity(boolean liquidity) {
    return inputsById
        .values()
        .parallelStream()
        .filter(input -> liquidity == input.isLiquidity())
        .collect(Collectors.toList());
  }

  public long sumAmount() {
    return inputsById.values().stream().mapToLong(input -> input.getOutPoint().getValue()).sum();
  }

  public Collection<RegisteredInput> _getInputs() {
    return inputsById.values();
  }
}
