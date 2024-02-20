package com.samourai.whirlpool.server.beans;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.util.RandomUtil;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.utils.Utils;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class InputPool {
  private Map<String, RegisteredInput> inputsById;

  public InputPool() {
    this.inputsById = new ConcurrentHashMap<>();
  }

  public void register(RegisteredInput registeredInput) {
    // overwrite if it was already registered
    String inputId = Utils.computeInputId(registeredInput);
    inputsById.put(inputId, registeredInput);
  }

  public Optional<RegisteredInput> findByUsername(String username) {
    return inputsById.values().parallelStream()
        .filter(registeredInput -> username.equals(registeredInput.getUsername()))
        .findFirst();
  }

  public boolean hasInput(RegisteredInput registeredInput) {
    return find(registeredInput).isPresent();
  }

  public Optional<RegisteredInput> find(String utxoHash, long utxoIndex, String username) {
    String inputId = Utils.computeInputId(utxoHash, utxoIndex, username);
    return Optional.ofNullable(inputsById.get(inputId));
  }

  public Optional<RegisteredInput> find(RegisteredInput registeredInput) {
    String inputId = Utils.computeInputId(registeredInput);
    return Optional.ofNullable(inputsById.get(inputId));
  }

  public Optional<RegisteredInput> findByOutPoint(TxOutPoint outPoint) {
    return findByOutPoint(outPoint.getHash(), outPoint.getIndex());
  }

  public Optional<RegisteredInput> findByOutPoint(String utxoHash, long utxoIndex) {
    String outpointId = Utils.computeOutpointId(utxoHash, utxoIndex);
    return inputsById.values().stream()
        .filter(confirmedInput -> outpointId.equals(confirmedInput.getOutPoint().toKey()))
        .findFirst();
  }

  public boolean hasOutPoint(TxOutPoint outPoint) {
    return findByOutPoint(outPoint).isPresent();
  }

  public Optional<RegisteredInput> findBySorobanSender(PaymentCode sender) {
    return inputsById.values().stream()
        .filter(
            confirmedInput ->
                confirmedInput.getSorobanInput() != null
                    && confirmedInput.getSorobanInput().getSender().equals(sender))
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
    return inputsById.values().parallelStream()
        .filter(registeredInput -> registeredInput.isQuarantine() == quarantine)
        .collect(Collectors.toList());
  }

  public Collection<String> getQuarantineDetails() {
    return findByQuarantine(true).stream()
        .map(input -> input.getOutPoint().toKey() + ": " + input.getQuarantineReason())
        .collect(Collectors.toList());
  }

  public synchronized Optional<RegisteredInput> removeRandom(Predicate<RegisteredInput> filter) {
    List<String> eligibleInputIds =
        inputsById.entrySet().parallelStream()
            .filter(e -> filter.test(e.getValue()))
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

  public synchronized Optional<RegisteredInput> remove(RegisteredInput registeredInput) {
    return removeBy(find(registeredInput));
  }

  public synchronized Optional<RegisteredInput> removeBySorobanSender(PaymentCode sender) {
    return removeBy(findBySorobanSender(sender));
  }

  protected Optional<RegisteredInput> removeBy(Optional<RegisteredInput> input) {
    if (input.isPresent()) {
      String inputId = Utils.computeInputId(input.get());
      inputsById.remove(inputId);
    }
    return input;
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

  public boolean hasInputs() {
    return !inputsById.isEmpty();
  }

  public int getSize() {
    return inputsById.size();
  }

  public int getSizeByTor(boolean tor) {
    return (int)
        inputsById.values().parallelStream()
            .filter(input -> Boolean.valueOf(tor).equals(input.getTor()))
            .count();
  }

  public int getSizeBySoroban(boolean soroban) {
    return getListBySoroban(soroban).size();
  }

  public Collection<RegisteredInput> getListBySoroban(boolean soroban) {
    return inputsById.values().parallelStream()
        .filter(input -> soroban == input.isSoroban())
        .collect(Collectors.toList());
  }

  public Collection<SorobanInput> getListSorobanInputs() {
    return getListBySoroban(true).parallelStream()
        .map(confirmedInput -> confirmedInput.getSorobanInput())
        .collect(Collectors.toList());
  }

  public int getSizeByLiquidity(boolean liquidity) {
    return getListByLiquidity(liquidity).size();
  }

  public Collection<RegisteredInput> getListByLiquidity(boolean liquidity) {
    return inputsById.values().parallelStream()
        .filter(input -> liquidity == input.isLiquidity())
        .collect(Collectors.toList());
  }

  public long sumAmount() {
    return inputsById.values().stream().mapToLong(input -> input.getOutPoint().getValue()).sum();
  }

  public Collection<RegisteredInput> _getInputs() {
    return inputsById.values();
  }

  public Collection<RegisteredInput> getListSorobanInputsExpired(long minLastSeen) {
    return getListBySoroban(true).stream()
        .filter(
            registeredInput -> registeredInput.getSorobanInput().getSorobanLastSeen() < minLastSeen)
        .collect(Collectors.toList());
  }
}
