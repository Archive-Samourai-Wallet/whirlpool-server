package com.samourai.whirlpool.server.beans;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import com.samourai.whirlpool.server.exceptions.ServerErrorCode;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.bitcoinj.core.Transaction;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mix {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MixTO mixTO;
  private Long created;

  private String mixId;
  private AsymmetricCipherKeyPair keyPair;
  private byte[] publicKey;
  private Timestamp timeStarted;
  private Map<MixStatus, Timestamp> timeStatus;

  private Pool pool;
  private int surge;
  private boolean confirmingSurge;

  private MixStatus mixStatus;
  private InputPool confirmingInputs;
  private Map<String, ConfirmedInput> inputsById;

  private Set<byte[]> bordereaux;
  private Set<String> receiveAddresses;
  private String lastReceiveAddressesRejected;
  private Map<String, String> revealedReceiveAddressesByUsername;
  private Map<String, Boolean> signed;

  private Transaction tx;
  private FailReason failReason;
  private String failInfo;

  public Mix(String mixId, Pool pool, CryptoService cryptoService) {
    this.mixTO = null;
    this.created = null;
    this.mixId = mixId;
    this.keyPair = cryptoService.generateKeyPair();
    try {
      this.publicKey = cryptoService.computePublicKey(keyPair).getEncoded();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    this.timeStarted = new Timestamp(System.currentTimeMillis());
    this.timeStatus = new ConcurrentHashMap<>();

    this.pool = pool;
    this.surge = 0; // will be set when enought mustmix confirmed

    this.mixStatus = MixStatus.CONFIRM_INPUT;
    this.confirmingInputs = new InputPool();
    this.inputsById = new ConcurrentHashMap<>();

    this.bordereaux = new HashSet<>();
    this.receiveAddresses = new HashSet<>();
    this.lastReceiveAddressesRejected = null;
    this.revealedReceiveAddressesByUsername = new ConcurrentHashMap<>();
    this.signed = new ConcurrentHashMap<>();

    this.tx = null;
    this.failReason = null;
    this.failInfo = null;
  }

  public MixTO computeMixTO() {
    if (mixTO == null) {
      mixTO = new MixTO();
    }
    Long feesAmount = null;
    Long feesPrice = null;
    if (tx != null) {
      feesAmount = tx.getFee().getValue();
      feesPrice = feesAmount / tx.getVirtualTransactionSize();
    }
    mixTO.setFrom(this, feesAmount, feesPrice);
    return mixTO;
  }

  public Optional<MixTO> __getMixTO() {
    return Optional.ofNullable(mixTO);
  }

  private boolean hasMinMustMixAndFeeReachedWithAdditionalMustMix(
      RegisteredInput additionalMustMix) {
    long additionalMinerFee = additionalMustMix.computeMinerFees(pool);
    return hasMinMustMixAndFeeReached(
        getNbInputsMustMix() + 1, computeMinerFeeAccumulated() + additionalMinerFee);
  }

  public boolean hasMinMustMixAndFeeReached() {
    return hasMinMustMixAndFeeReached(getNbInputsMustMix(), computeMinerFeeAccumulated());
  }

  private boolean hasMinMustMixAndFeeReached(int nbMustMix, long minerFeeAccumulated) {
    // verify minMustMix
    if (nbMustMix < pool.getMinMustMix()) {
      return false;
    }

    // verify minerFeeMix
    if (minerFeeAccumulated < pool.getMinerFeeMix()) {
      return false;
    }
    return true;
  }

  public boolean hasMinLiquidityMixReached() {
    return getAvailableSlotsLiquidityMin() < 1;
  }

  // may be negative
  private int getAvailableSlotsLiquidityMin() {
    return pool.getMinLiquidity() - getNbInputsLiquidities();
  }

  // may be negative
  public int getAvailableSlotsLiquidityAndSurge() {
    if (!hasMinMustMixAndFeeReached()) {
      // not enough mustMixs => add minimal liquidities, and wait for more mustMixs
      return getAvailableSlotsLiquidityMin();
    }
    // enough mustMixs => add missing liquidities & surges
    int mustMixSlots = Math.max(getNbInputsMustMix(), pool.getMinMustMix());
    return pool.getAnonymitySet() + surge - mustMixSlots - getNbInputsLiquidities();
  }

  // may be negative
  public int getExessLiquidityAndSurge() {
    return getAvailableSlotsLiquidityAndSurge() * -1;
  }

  public int getAvailableSlotsMustMix() {
    if (surge > 0) {
      return 0; // no more mustMix while accepting surges
    }
    int liquiditySlots = Math.max(getNbInputsLiquidities(), pool.getMinLiquidity());
    return pool.getAnonymitySet() - liquiditySlots - getNbInputsMustMix();
  }

  public synchronized void hasAvailableSlotFor(RegisteredInput registeredInput)
      throws QueueInputException {
    if (isFullWithSurge()) {
      throw new QueueInputException("Current mix is full", registeredInput, pool.getPoolId());
    }

    if (registeredInput.isLiquidity()) {
      // verify minMustMix
      if (getAvailableSlotsLiquidityAndSurge() < 1) {
        throw new QueueInputException(
            "Current mix is full for liquidity", registeredInput, pool.getPoolId());
      }
    } else {
      // verify mustMix
      int mustMixRemaining = getAvailableSlotsMustMix();
      if (mustMixRemaining < 1) {
        throw new QueueInputException(
            "Current mix is full for mustMix", registeredInput, pool.getPoolId());
      }

      // last mustMix: verify enough miner-fees to pay the mix
      if (mustMixRemaining == 1) {
        if (!hasMinMustMixAndFeeReachedWithAdditionalMustMix(registeredInput)) {
          log.warn(
              "["
                  + pool.getPoolId()
                  + "] Queueing last mustMix: insufficient minerFees: "
                  + registeredInput);
          throw new QueueInputException(
              "Not enough minerFee for last mustMix slot", registeredInput, pool.getPoolId());
        }
        if (log.isTraceEnabled()) {
          log.trace("[" + pool.getPoolId() + "] Accepting last mustMix: " + registeredInput);
        }
      }
    }
  }

  private int computeSurge() {
    if (!hasMinMustMixAndFeeReached()) {
      // no surge allowed yet
      return 0;
    }

    // compute possible surges for minerFeeAccumulated
    long minerFeeForSurges = computeMinerFeeAccumulated() - pool.getMinerFeeMix();
    if (minerFeeForSurges <= 0) {
      return 0;
    }
    int surges = (int) Math.floor(minerFeeForSurges / pool.getMinerFee().getSurgeRelayFee());

    // apply pool surge limit
    int poolSurgeLimit = pool.getSurge();
    surges = Math.min(surges, poolSurgeLimit);
    if (log.isDebugEnabled()) {
      log.debug(
          "["
              + getLogId()
              + "] computeSurges() = "
              + surges
              + ": minerFeeForSurges="
              + minerFeeForSurges
              + ", surgeRelayFee="
              + pool.getMinerFee().getSurgeRelayFee()
              + ", poolSurgeLimit="
              + poolSurgeLimit);
    }
    return surges;
  }

  public synchronized void setSurge() {
    // update surge limit for mix
    int newSurge = computeSurge();
    if (surge != newSurge) {
      if (log.isDebugEnabled()) {
        log.debug("[" + getLogId() + "] setSurge: " + surge + "->" + newSurge);
      }
      this.surge = newSurge;
      if (surge == 0) {
        confirmingSurge = false;
      }
    }
  }

  public boolean isConfirmingSurge() {
    return confirmingSurge;
  }

  public void setConfirmingSurge(boolean confirmingSurge) {
    this.confirmingSurge = confirmingSurge;
    if (log.isDebugEnabled()) {
      log.debug("[" + getLogId() + "] confirmingSurge=" + confirmingSurge);
    }
  }

  public boolean isAnonymitySetReached() {
    return (getNbInputs() >= pool.getAnonymitySet());
  }

  public boolean isFullWithSurge() {
    return (getNbInputs() >= (pool.getAnonymitySet() + surge));
  }

  public String getMixId() {
    return mixId;
  }

  public String getLogId() {
    return pool.getPoolId() + "/" + mixId;
  }

  public AsymmetricCipherKeyPair getKeyPair() {
    return keyPair;
  }

  public byte[] getPublicKey() {
    return publicKey;
  }

  public Timestamp getTimeStarted() {
    return timeStarted;
  }

  public Map<MixStatus, Timestamp> getTimeStatus() {
    return timeStatus;
  }

  public Pool getPool() {
    return pool;
  }

  public int getSurge() {
    return surge;
  }

  public int getAnonymitySetWithSurge() {
    return pool.getAnonymitySet() + surge;
  }

  public MixStatus getMixStatus() {
    return mixStatus;
  }

  public void setMixStatusAndTime(MixStatus mixStatus) {
    this.mixStatus = mixStatus;
    timeStatus.put(mixStatus, new Timestamp(System.currentTimeMillis()));
  }

  public boolean hasConfirmingInput(TxOutPoint txOutPoint) {
    return confirmingInputs.hasInput(txOutPoint);
  }

  public synchronized void registerConfirmingInput(RegisteredInput registeredInput)
      throws NotifiableException {
    confirmingInputs.register(registeredInput);
    if (this.created == null) {
      timeStatus.put(MixStatus.CONFIRM_INPUT, new Timestamp(System.currentTimeMillis()));
      this.created = System.currentTimeMillis();
    }
  }

  public synchronized Optional<RegisteredInput> removeConfirmingInputByUsername(String username) {
    Optional<RegisteredInput> confirmingInput = confirmingInputs.removeByUsername(username);
    if (confirmingInput.isPresent()) {
      if (log.isTraceEnabled()) {
        log.trace("[" + getLogId() + "] " + username + " unregistered from confirming inputs");
      }
    }
    return confirmingInput;
  }

  public int getNbConfirmingInputs() {
    return confirmingInputs.getSize();
  }

  public synchronized Collection<RegisteredInput> clearConfirmingInputs() {
    return confirmingInputs.clear();
  }

  public Collection<ConfirmedInput> getInputs() {
    return inputsById.values();
  }

  public Optional<ConfirmedInput> getInputByUsername(String username) {
    return inputsById.values().stream()
        .filter(
            confirmedInput -> confirmedInput.getRegisteredInput().getUsername().equals(username))
        .findFirst();
  }

  public Optional<ConfirmedInput> getInputByAddress(String address) {
    return inputsById.values().stream()
        .filter(
            confirmedInput ->
                confirmedInput
                    .getRegisteredInput()
                    .getOutPoint()
                    .getToAddress()
                    .toLowerCase()
                    .equals(address.toLowerCase()))
        .findFirst();
  }

  public int getNbInputs() {
    return inputsById.size();
  }

  public int getNbInputsNonSurge() {
    return Math.min(getNbInputs(), pool.getAnonymitySet());
  }

  public int getNbInputsSurge() {
    return Math.max(getNbInputs() - pool.getAnonymitySet(), 0);
  }

  public int getNbInputsMustMix() {
    return (int)
        getInputs()
            .parallelStream()
            .filter(input -> !input.getRegisteredInput().isLiquidity())
            .count();
  }

  public int getNbInputsLiquidities() {
    return (int)
        getInputs()
            .parallelStream()
            .filter(input -> input.getRegisteredInput().isLiquidity())
            .count();
  }

  public long computeMinerFeeAccumulated() {
    return getInputs()
        .parallelStream()
        .filter(input -> !input.getRegisteredInput().isLiquidity())
        .map(input -> input.getRegisteredInput().computeMinerFees(pool))
        .reduce(0L, Long::sum);
  }

  public synchronized void registerInput(ConfirmedInput confirmedInput)
      throws IllegalInputException {
    String inputId = Utils.computeInputId(confirmedInput.getRegisteredInput().getOutPoint());
    if (inputsById.containsKey(inputId)) {
      throw new IllegalInputException(
          ServerErrorCode.INPUT_ALREADY_REGISTERED, "input already registered");
    }
    inputsById.put(inputId, confirmedInput);
  }

  public synchronized void unregisterInputLiquidities(int limit) {
    List<ConfirmedInput> liquidities =
        getInputs().stream()
            .filter(i -> i.getRegisteredInput().isLiquidity())
            .limit(limit)
            .collect(Collectors.toList());
    for (ConfirmedInput liquidity : liquidities) {
      unregisterInput(liquidity);
    }
  }

  public synchronized void unregisterInput(ConfirmedInput confirmedInput) {
    log.info(
        "["
            + getLogId()
            + "] "
            + " unregistering a CONFIRMED input, username="
            + confirmedInput.getRegisteredInput().getUsername());
    String inputId = Utils.computeInputId(confirmedInput.getRegisteredInput().getOutPoint());
    inputsById.remove(inputId);
  }

  public ConfirmedInput findInput(TxOutPoint outPoint) {
    return inputsById.get(Utils.computeInputId(outPoint));
  }

  public String computeInputsHash() {
    Collection<Utxo> inputs =
        getInputs()
            .parallelStream()
            .map(confirmedInput -> confirmedInput.getRegisteredInput().getOutPoint())
            .map(input -> new Utxo(input.getHash(), input.getIndex()))
            .collect(Collectors.toList());
    return WhirlpoolProtocol.computeInputsHash(inputs);
  }

  public synchronized void registerOutput(String receiveAddress, byte[] bordereau) {
    receiveAddresses.add(receiveAddress);
    bordereaux.add(bordereau);
  }

  public void setLastReceiveAddressesRejected(String lastReceiveAddressesRejected) {
    this.lastReceiveAddressesRejected = lastReceiveAddressesRejected;
  }

  public String getLastReceiveAddressesRejected() {
    if (!MixStatus.REGISTER_OUTPUT.equals(mixStatus)) {
      return null;
    }
    return lastReceiveAddressesRejected;
  }

  public long getElapsedTime() {
    long elapsedTime = System.currentTimeMillis() - getTimeStarted().getTime();
    return elapsedTime;
  }

  public Set<String> getReceiveAddresses() {
    return receiveAddresses;
  }

  public boolean hasReceiveAddress(String receiveAddress) {
    return receiveAddresses.contains(receiveAddress);
  }

  public boolean hasBordereau(byte[] bordereau) {
    return bordereaux.contains(bordereau);
  }

  public boolean hasRevealedOutputUsername(String username) {
    return revealedReceiveAddressesByUsername.containsKey(username);
  }

  public boolean hasRevealedReceiveAddress(String receiveAddress) {
    return revealedReceiveAddressesByUsername.containsValue(receiveAddress);
  }

  public void addRevealedOutput(String username, String receiveAddress) {
    revealedReceiveAddressesByUsername.put(username, receiveAddress);
  }

  public List<ConfirmedInput> getInputsNotRevealedOutput() {
    return getInputs()
        .parallelStream()
        .filter(input -> !hasRevealedOutputUsername(input.getRegisteredInput().getUsername()))
        .collect(Collectors.toList());
  }

  public int getNbRevealedOutputs() {
    return revealedReceiveAddressesByUsername.size();
  }

  public int getNbSignatures() {
    return signed.size();
  }

  public boolean getSignedByUsername(String username) {
    return signed.containsKey(username);
  }

  public void setSignedByUsername(String username) {
    signed.put(username, true);
  }

  public List<ConfirmedInput> getInputsNotSigned() {
    return getInputs()
        .parallelStream()
        .filter(input -> !getSignedByUsername(input.getRegisteredInput().getUsername()))
        .collect(Collectors.toList());
  }

  public void setTx(Transaction tx) {
    this.tx = tx;
  }

  public Transaction getTx() {
    return tx;
  }

  public void setFailReason(FailReason failReason) {
    this.failReason = failReason;
  }

  public FailReason getFailReason() {
    return failReason;
  }

  public void setFailInfo(String failInfo) {
    this.failInfo = failInfo;
  }

  public String getFailInfo() {
    return failInfo;
  }

  public long computeAmountIn() {
    return inputsById.values().stream()
        .mapToLong(input -> input.getRegisteredInput().getOutPoint().getValue())
        .sum();
  }

  public long computeAmountOut() {
    return getNbInputs() * getPool().getDenomination();
  }

  public int computeMixDuration() {
    if (this.created == null) {
      return 0;
    }
    int mixDuration = (int) ((System.currentTimeMillis() - created) / 1000);
    return mixDuration;
  }

  public boolean isDone() {
    return MixStatus.FAIL.equals(getMixStatus()) || MixStatus.SUCCESS.equals(getMixStatus());
  }

  public boolean isBlamableStatus() {
    return !isDone() && !MixStatus.CONFIRM_INPUT.equals(getMixStatus());
  }
}
