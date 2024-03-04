package com.samourai.whirlpool.server.beans;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.sorobanClient.SorobanPayloadable;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.protocol.soroban.payload.beans.BlameReason;
import com.samourai.whirlpool.protocol.soroban.payload.mix.MixStatusResponseConfirmInput;
import com.samourai.whirlpool.protocol.soroban.payload.mix.MixStatusResponseFail;
import com.samourai.whirlpool.server.controllers.soroban.AbstractPerMixControllerSoroban;
import com.samourai.whirlpool.server.controllers.soroban.MixStatusControllerSoroban;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import com.samourai.whirlpool.server.services.CryptoService;
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
  private InputPool confirmedInputs;

  private Set<byte[]> bordereaux;
  private Set<String> receiveAddresses;
  private String lastReceiveAddressesRejected;
  private Map<String, String> revealedReceiveAddressesByUsername;
  private Map<String, Boolean> signed;

  private Transaction tx;
  private FailReason failReason;
  private String failInfo;
  private List<String> blameInputSenders;
  private BlameReason blameReason;
  private SorobanPayloadable mixStatusResponse;

  private AbstractPerMixControllerSoroban sorobanControllerMixStep;
  private MixStatusControllerSoroban sorobanControllerMixStatus;

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
    this.confirmedInputs = new InputPool();

    this.bordereaux = new HashSet<>();
    this.receiveAddresses = new HashSet<>();
    this.lastReceiveAddressesRejected = null;
    this.revealedReceiveAddressesByUsername = new ConcurrentHashMap<>();
    this.signed = new ConcurrentHashMap<>();

    this.tx = null;
    this.failReason = null;
    this.failInfo = null;
    this.blameInputSenders = new LinkedList<>();
    this.blameReason = null;
    this.mixStatusResponse = new MixStatusResponseConfirmInput();

    this.sorobanControllerMixStep = null;
    this.sorobanControllerMixStatus = null;
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

  public void hasAvailableSlotFor(RegisteredInput registeredInput) throws QueueInputException {
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
                  + "] Queueing last mustMix: insufficient minerFees: minerFeeAccumulated="
                  + computeMinerFeeAccumulated()
                  + ", minerFeeMix="
                  + pool.getMinerFeeMix()
                  + ", mustMix="
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
    if (pool.isSurgeDisabledForLowLiquidityPool()) {
      if (surge != 0) {
        log.warn("MIX_SURGE_DISABLE " + mixId + " because of low liquidity pool");
      }
      return 0;
    }
    if (pool.getSurge() < 1) {
      return 0;
    }

    // compute possible surges for minerFeeAccumulated
    long minerFeeAccumulated = computeMinerFeeAccumulated();
    long minRelaySatPerB = pool.getMinerFee().getMinRelaySatPerB();
    int surges = 0;
    for (int i = 1; i <= pool.getSurge(); i++) {
      long txSize = pool.computeTxSize(i);
      float satPerB = ((float) minerFeeAccumulated) / txSize;
      if (log.isTraceEnabled()) {
        log.trace(
            "MIX_SURGE "
                + mixId
                + " computeSurge("
                + i
                + "): "
                + "minerFeeAccumulated="
                + minerFeeAccumulated
                + ", txSize="
                + txSize
                + ", satPerB="
                + satPerB
                + " vs minRelaySatPerB="
                + minRelaySatPerB);
      }
      if (satPerB < pool.getMinerFee().getMinRelaySatPerB()) {
        break;
      }
      surges = i;
    }
    return surges;
  }

  public void setSurge() {
    // update surge limit for mix
    int newSurge = computeSurge();
    if (surge != newSurge) {
      if (log.isDebugEnabled()) {
        log.debug("MIX_SURGE_CHANGE " + mixId + " " + surge + "->" + newSurge);
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
      log.debug("SET_CONFIRMING_SURGE=" + confirmingSurge + " " + mixId);
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

  public String getLogStatus() {
    int liquiditiesQueued = pool.getLiquidityQueue().getSize();
    int mustMixQueued = pool.getMustMixQueue().getSize();
    return getMixStatus()
        + " "
        + getNbInputs()
        + "/"
        + getAnonymitySetWithSurge()
        + " inputs, "
        + getNbInputsMustMix()
        + "/"
        + getPool().getMinMustMix()
        + " mustMix, "
        + getNbInputsLiquidities()
        + "/"
        + getPool().getMinLiquidity()
        + " liquidity, "
        + getNbInputsSurge()
        + "/"
        + getSurge()
        + " surge, "
        + computeMinerFeeAccumulated()
        + "/"
        + getPool().getMinerFeeMix()
        + "sat"
        + ", "
        + getNbConfirmingInputs()
        + " confirming"
        + ", "
        + liquiditiesQueued
        + "+"
        + mustMixQueued
        + " queued";
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

  public boolean hasConfirmingInput(RegisteredInput registeredInput) {
    return confirmingInputs.hasInput(registeredInput);
  }

  public void registerConfirmingInput(RegisteredInput registeredInput) {
    registeredInput.setConfirmingSince(System.currentTimeMillis());
    confirmingInputs.register(registeredInput);
    log.info(
        "MIX_ADD_CONFIRMING_"
            + registeredInput.getTypeStr()
            + " "
            + mixId
            + " "
            + registeredInput.toString());
    if (this.created == null) {
      timeStatus.put(MixStatus.CONFIRM_INPUT, new Timestamp(System.currentTimeMillis()));
      this.created = System.currentTimeMillis();
    }
  }

  public synchronized Optional<RegisteredInput> removeConfirmingInputByUsername(String username) {
    return removeConfirmingInputBy(confirmingInputs.removeByUsername(username));
  }

  public synchronized Optional<RegisteredInput> removeConfirmingInput(
      RegisteredInput registeredInput) {
    return removeConfirmingInputBy(confirmingInputs.remove(registeredInput));
  }

  public synchronized Optional<RegisteredInput> removeConfirmingInputBySender(PaymentCode sender) {
    return removeConfirmingInputBy(confirmingInputs.removeBySorobanSender(sender));
  }

  protected Optional<RegisteredInput> removeConfirmingInputBy(
      Optional<RegisteredInput> confirmingInput) {
    if (confirmingInput.isPresent()) {
      log.info(
          "MIX_REMOVE_CONFIRMING_"
              + confirmingInput.get().getTypeStr()
              + " "
              + mixId
              + " "
              + confirmingInput.get());
      confirmingInput.get().setConfirmingSince(null);
    }
    return confirmingInput;
  }

  public void cleanConfirmingInputs(long minConfirmingSince) {
    List<RegisteredInput> expiredInputs =
        confirmingInputs._getInputs().stream()
            .filter(registeredInput -> registeredInput.getConfirmingSince() < minConfirmingSince)
            .collect(Collectors.toList());
    expiredInputs.stream().forEach(registeredInput -> removeConfirmingInput(registeredInput));
  }

  public int getNbConfirmingInputs() {
    return confirmingInputs.getSize();
  }

  public InputPool getConfirmingInputs() {
    return confirmingInputs;
  }

  public synchronized Collection<RegisteredInput> clearConfirmingInputs() {
    confirmingInputs._getInputs().forEach(input -> input.setConfirmingSince(null));
    return confirmingInputs.clear();
  }

  public InputPool getInputs() {
    return confirmedInputs;
  }

  public int getNbInputs() {
    return confirmedInputs.getSize();
  }

  public int getNbInputsNonSurge() {
    return Math.min(getNbInputs(), pool.getAnonymitySet());
  }

  public int getNbInputsSurge() {
    return Math.max(getNbInputs() - pool.getAnonymitySet(), 0);
  }

  public int getNbInputsMustMix() {
    return getInputs().getSizeByLiquidity(false);
  }

  public Collection<RegisteredInput> getInputsMustMix() {
    return getInputs().getListByLiquidity(false);
  }

  public int getNbInputsLiquidities() {
    return getInputs().getSizeByLiquidity(true);
  }

  public Collection<RegisteredInput> getInputsLiquidities() {
    return getInputs().getListByLiquidity(true);
  }

  public long computeMinerFeeAccumulated() {
    return getInputs()._getInputs().parallelStream()
        .filter(input -> !input.isLiquidity())
        .map(input -> input.computeMinerFees(pool))
        .reduce(0L, Long::sum);
  }

  public synchronized void registerInput(RegisteredInput registeredInput, byte[] signedBordereau)
      throws IllegalInputException {
    if (confirmedInputs.find(registeredInput).isPresent()) {
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED, "input already registered");
    }
    registeredInput.setSignedBordereau(signedBordereau);
    confirmedInputs.register(registeredInput);
    log.info("MIX_ADD_INPUT_" + registeredInput.getTypeStr() + " " + mixId + " " + registeredInput);
  }

  public synchronized void unregisterInputLiquidities(int limit) {
    getInputs()._getInputs().stream()
        .filter(i -> i.isLiquidity())
        .limit(limit)
        .forEach(liquidity -> unregisterInput(liquidity));
  }

  public synchronized void unregisterInput(RegisteredInput confirmedInput) {
    log.info("MIX_REMOVE_INPUT " + mixId + " " + confirmedInput);
    confirmedInputs.remove(confirmedInput);
    confirmedInput.setSignedBordereau(null);
  }

  public String computeInputsHash() {
    Collection<Utxo> inputs =
        getInputs()._getInputs().parallelStream()
            .map(confirmedInput -> confirmedInput.getOutPoint())
            .map(input -> new Utxo(input.getHash(), input.getIndex()))
            .collect(Collectors.toList());
    return WhirlpoolProtocol.computeInputsHash(inputs);
  }

  public void registerOutput(String receiveAddress, byte[] bordereau) {
    receiveAddresses.add(receiveAddress);
    bordereaux.add(bordereau);
    log.info(
        "MIX_REGISTER_OUTPUT "
            + mixId
            + " "
            + receiveAddress
            + " "
            + receiveAddresses.size()
            + "/"
            + getNbInputs());
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

  public boolean hasRevealedOutput(RegisteredInput registeredInput) {
    return revealedReceiveAddressesByUsername.containsKey(registeredInput.getUsername());
  }

  public boolean hasRevealedReceiveAddress(String receiveAddress) {
    return revealedReceiveAddressesByUsername.containsValue(receiveAddress);
  }

  public void addRevealedOutput(RegisteredInput registeredInput, String receiveAddress) {
    log.info("MIX_REVEAL_OUTPUT " + mixId + " " + registeredInput.toString());
    revealedReceiveAddressesByUsername.put(registeredInput.getUsername(), receiveAddress);
  }

  public List<RegisteredInput> getInputsNotRevealedOutput() {
    return getInputs()._getInputs().parallelStream()
        .filter(input -> !hasRevealedOutput(input))
        .collect(Collectors.toList());
  }

  public int getNbRevealedOutputs() {
    return revealedReceiveAddressesByUsername.size();
  }

  public int getNbSignatures() {
    return signed.size();
  }

  public boolean isSigned(RegisteredInput registeredInput) {
    return signed.containsKey(registeredInput.getUsername());
  }

  public void setSigned(RegisteredInput registeredInput) {
    log.info("MIX_SIGN " + mixId + " " + registeredInput);
    signed.put(registeredInput.getUsername(), true);
  }

  public List<RegisteredInput> getInputsNotSigned() {
    return getInputs()._getInputs().parallelStream()
        .filter(input -> !isSigned(input))
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
    return confirmedInputs.sumAmount();
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

  public void setBlameInputs(List<RegisteredInput> blameInputs, BlameReason blameReason) {
    this.blameInputSenders =
        blameInputs.stream()
            .filter(registeredInput -> registeredInput.isSoroban())
            .map(registeredInput -> registeredInput.getSorobanInput().getSender().toString())
            .collect(Collectors.toList());
    this.blameReason = blameReason;
  }

  public BlameReason getBlameReason() {
    return blameReason;
  }

  public SorobanPayloadable getMixStatusResponse(SorobanInput sorobanInput) {
    if (mixStatusResponse instanceof MixStatusResponseFail) {
      // set blame info depending on sorobanInput
      boolean isBlameInput =
          blameInputSenders != null
              && blameInputSenders.contains(sorobanInput.getSender().toString());
      BlameReason blame = isBlameInput ? blameReason : null;
      ((MixStatusResponseFail) mixStatusResponse).blame = blame;
    }
    return mixStatusResponse;
  }

  public void setMixStatusResponse(SorobanPayloadable mixStatusResponse) {
    this.mixStatusResponse = mixStatusResponse;
  }

  public AbstractPerMixControllerSoroban getSorobanControllerMixStep() {
    return sorobanControllerMixStep;
  }

  public void setSorobanControllerMixStep(
      AbstractPerMixControllerSoroban sorobanControllerMixStep) {
    this.sorobanControllerMixStep = sorobanControllerMixStep;
  }

  public MixStatusControllerSoroban getSorobanControllerMixStatus() {
    return sorobanControllerMixStatus;
  }

  public void setSorobanControllerMixStatus(MixStatusControllerSoroban sorobanControllerMixStatus) {
    this.sorobanControllerMixStatus = sorobanControllerMixStatus;
  }
}
