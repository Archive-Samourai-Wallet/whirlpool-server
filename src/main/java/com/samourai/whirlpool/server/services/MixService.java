package com.samourai.whirlpool.server.services;

import com.samourai.wallet.bip69.BIP69InputComparator;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.ConfirmInputResponse;
import com.samourai.whirlpool.protocol.websocket.notifications.*;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.beans.export.MixCsv;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.*;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MixService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private WSMessageService WSMessageService;
  private CryptoService cryptoService;
  private BlameService blameService;
  private DbService dbService;
  private RpcClientService rpcClientService;
  private MixLimitsService mixLimitsService;
  private Bech32UtilGeneric bech32Util;
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private PoolService poolService;
  private ExportService exportService;
  private MetricService metricService;
  private TaskService taskService;
  private TxUtil txUtil;

  private Map<String, Mix> currentMixs;

  private static int CONFIRM_INPUT_CHECK_DELAY = 3000;
  private static int CONFIRM_INPUT_INVITE_SURGE_DELAY = 10000;

  @Autowired
  public MixService(
      CryptoService cryptoService,
      BlameService blameService,
      DbService dbService,
      RpcClientService rpcClientService,
      WSMessageService WSMessageService,
      Bech32UtilGeneric bech32Util,
      WhirlpoolServerConfig whirlpoolServerConfig,
      MixLimitsService mixLimitsService,
      PoolService poolService,
      ExportService exportService,
      MetricService metricService,
      TaskService taskService,
      TxUtil txUtil,
      WSSessionService wsSessionService) {
    this.cryptoService = cryptoService;
    this.blameService = blameService;
    this.dbService = dbService;
    this.rpcClientService = rpcClientService;
    this.WSMessageService = WSMessageService;
    this.bech32Util = bech32Util;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    mixLimitsService.setMixService(this); // avoids circular reference
    this.mixLimitsService = mixLimitsService;
    this.poolService = poolService;
    this.exportService = exportService;
    this.metricService = metricService;
    this.taskService = taskService;
    this.txUtil = txUtil;

    this.__reset();

    // listen websocket onDisconnect
    wsSessionService.addOnDisconnectListener(username -> onClientDisconnect(username));
  }

  /** Last input validations when adding it to a mix (not when queueing it) */
  private void validateOnConfirmInput(
      Mix mix, RegisteredInput registeredInput, String userHashOrNull)
      throws QueueInputException, IllegalInputException {
    Pool pool = mix.getPool();

    // failMode
    try {
      whirlpoolServerConfig.checkFailMode(FailMode.CONFIRM_INPUT_BLAME);
    } catch (Exception e) {
      blameService.blame(registeredInput, BlameReason.DISCONNECT, mix);
      throw new IllegalInputException(ServerErrorCode.INPUT_REJECTED, e.getMessage());
    }

    // check mix didn't start yet
    if (!MixStatus.CONFIRM_INPUT.equals(mix.getMixStatus())) {
      // confirming input too late => enqueue in pool
      String poolId = mix.getPool().getPoolId();
      throw new QueueInputException("Mix already started", registeredInput, poolId);
    }

    // verify mix not full
    mix.hasAvailableSlotFor(registeredInput); // throws QueueInputException

    // verify unique userHash
    int maxInputsSameUserHash = whirlpoolServerConfig.getRegisterInput().getMaxInputsSameUserHash();
    if (userHashOrNull != null) {
      long countInputSameUserHash =
          mix.getInputs()
              .parallelStream()
              .filter(input -> input.getUserHash().equals(userHashOrNull))
              .count();
      if ((countInputSameUserHash + 1) > maxInputsSameUserHash) {
        if (log.isTraceEnabled()) {
          log.trace(
              "already "
                  + countInputSameUserHash
                  + " inputs with same userHash in "
                  + mix.getMixId()
                  + ": "
                  + userHashOrNull);
        }
        throw new QueueInputException(
            "Your wallet already registered for this mix", registeredInput, pool.getPoolId());
      }
    }

    // verify max-inputs-same-hash
    String inputHash = registeredInput.getOutPoint().getHash();
    int maxInputsSameHash = whirlpoolServerConfig.getRegisterInput().getMaxInputsSameHash();
    long countInputsSameHash =
        mix.getInputs()
            .parallelStream()
            .filter(input -> input.getRegisteredInput().getOutPoint().getHash().equals(inputHash))
            .count();
    if ((countInputsSameHash + 1) > maxInputsSameHash) {
      if (log.isTraceEnabled()) {
        log.trace("already " + countInputsSameHash + " inputs with same hash: " + inputHash);
      }
      throw new QueueInputException(
          "Current mix is full for inputs with same hash", registeredInput, pool.getPoolId());
    }

    // verify no input address reuse with other inputs
    String inputAddress = registeredInput.getOutPoint().getToAddress();
    if (mix.getInputByAddress(inputAddress).isPresent()) {
      throw new QueueInputException(
          "Current mix is full for inputs with same address", registeredInput, pool.getPoolId());
    }

    // verify input not already confirmed
    ConfirmedInput alreadyConfirmedInput = mix.findInput(registeredInput.getOutPoint());
    if (alreadyConfirmedInput != null) {
      // input already confirmed => reject duplicate client
      throw new IllegalInputException(
          ServerErrorCode.INPUT_ALREADY_REGISTERED, "Input already confirmed");
    }
  }

  public void validateForConfirmInput(Mix mix, RegisteredInput registeredInput)
      throws QueueInputException, IllegalInputException {
    String userHash = registeredInput.getLastUserHash(); // may be null
    validateOnConfirmInput(mix, registeredInput, userHash);
  }

  private void validateOnConfirmInput(Mix mix, ConfirmedInput confirmedInput)
      throws QueueInputException, IllegalInputException {
    RegisteredInput registeredInput = confirmedInput.getRegisteredInput();
    validateOnConfirmInput(mix, registeredInput, confirmedInput.getUserHash());
  }

  public synchronized byte[] confirmInput(
      String mixId, String username, byte[] blindedBordereau, String userHash)
      throws IllegalInputException, MixException, QueueInputException {
    Mix mix = getMix(mixId);

    // find confirming input
    RegisteredInput registeredInput =
        mix.removeConfirmingInputByUsername(username)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        ServerErrorCode.SERVER_ERROR,
                        "Confirming input not found: username=" + username));

    // set lastUserHash
    registeredInput.setLastUserHash(userHash);

    ConfirmedInput confirmedInput = new ConfirmedInput(registeredInput, userHash);

    // last input validations
    validateOnConfirmInput(mix, confirmedInput);

    // sign bordereau to reply
    byte[] signedBordereau = cryptoService.signBlindedOutput(blindedBordereau, mix.getKeyPair());

    // add to mix inputs
    mix.registerInput(confirmedInput);
    log.info(
        "["
            + mixId
            + "] confirmed "
            + (registeredInput.isLiquidity() ? "liquidity" : "mustMix")
            + ": "
            + registeredInput.getOutPoint());
    logMixStatus(mix);

    // log activity
    ActivityCsv activityCsv =
        new ActivityCsv("CONFIRM_INPUT", mix.getPool().getPoolId(), registeredInput, null, null);
    exportService.exportActivity(activityCsv);

    // reply confirmInputResponse with signedBordereau
    String signedBordereau64 = WhirlpoolProtocol.encodeBytes(signedBordereau);
    final ConfirmInputResponse confirmInputResponse =
        new ConfirmInputResponse(mixId, signedBordereau64);
    WSMessageService.sendPrivate(username, confirmInputResponse);

    // manage surges when enough mustMix confirmed
    if (!registeredInput.isLiquidity() && mix.hasMinMustMixAndFeeReached()) {
      if (log.isDebugEnabled()) {
        log.debug("Last mustMix confirmed, adjusting surge limit and inviting surges...");
      }
      // update mix surge limit
      mix.setSurge();
      // invite surges now (just like mixLimitsService do periodically)
      poolService.confirmInputs(mix, this);
    }

    // delay before checking mix ready, to make sure client processed confirmation
    int checkDelay = CONFIRM_INPUT_CHECK_DELAY;
    if (mix.getSurge() > 0 && mix.getAvailableSlotsLiquidityAndSurge() > 0) {
      checkDelay = CONFIRM_INPUT_INVITE_SURGE_DELAY; // delay for remaining surges to confirm
    }

    // check mix ready
    taskService.runOnce(
        checkDelay,
        () -> {
          checkConfirmInputReady(mix);
        });
    return signedBordereau;
  }

  private void checkConfirmInputReady(Mix mix) {
    if (!whirlpoolServerConfig.isMixEnabled()) {
      // mix disabled by server configuration
      return;
    }

    if (MixStatus.CONFIRM_INPUT.equals(mix.getMixStatus()) && isConfirmInputReady(mix)) {
      // all inputs confirmed => REGISTER_OUTPUT
      changeMixStatus(mix.getMixId(), MixStatus.REGISTER_OUTPUT);
    }
  }

  protected boolean isConfirmInputReady(Mix mix) {
    if (!mix.isAnonymitySetReached()) {
      return false;
    }
    if (!mix.hasMinMustMixAndFeeReached()) {
      return false;
    }
    if (!mix.hasMinLiquidityMixReached()) {
      return false;
    }
    // check for inputs spent in the meantime
    if (!revalidateInputsForSpent(mix)) {
      return false;
    }
    return true;
  }

  public synchronized void registerOutputFailure(String inputsHash, String receiveAddress)
      throws Exception {
    Mix mix = getMixByInputsHash(inputsHash, MixStatus.REGISTER_OUTPUT);
    mix.setLastReceiveAddressesRejected(receiveAddress);
    log.info("[" + mix.getMixId() + "] registered output failure: " + receiveAddress);
  }

  public synchronized Mix registerOutput(
      String inputsHash, byte[] unblindedSignedBordereau, String receiveAddress, byte[] bordereau)
      throws Exception {
    Mix mix = getMixByInputsHash(inputsHash, MixStatus.REGISTER_OUTPUT);

    // verify bordereau not already registered
    if (bordereau == null) {
      throw new IllegalInputException(ServerErrorCode.INVALID_ARGUMENT, "Invalid bordereau");
    }
    if (mix.hasBordereau(bordereau)) {
      throw new IllegalInputException(
          ServerErrorCode.INPUT_ALREADY_REGISTERED, "Bordereau already registered");
    }

    // verify receiveAddress not already registered
    if (StringUtils.isEmpty(receiveAddress)) {
      throw new IllegalInputException(ServerErrorCode.INVALID_ARGUMENT, "Invalid receiveAddress");
    }
    if (mix.hasReceiveAddress(receiveAddress)) {
      throw new IllegalInputException(
          ServerErrorCode.INPUT_ALREADY_REGISTERED, "receiveAddress already registered");
    }

    // verify unblindedSignedBordereau
    if (!cryptoService.verifyUnblindedSignedBordereau(
        bordereau, unblindedSignedBordereau, mix.getKeyPair())) {
      throw new IllegalInputException(
          ServerErrorCode.INVALID_ARGUMENT, "Invalid unblindedSignedBordereau");
    }

    // verify no output address reuse with inputs
    if (mix.getInputByAddress(receiveAddress).isPresent()) {
      throw new IllegalInputException(
          ServerErrorCode.INPUT_ALREADY_REGISTERED, "output already registered as input");
    }

    log.info("[" + mix.getMixId() + "] registered output: " + receiveAddress);
    mix.registerOutput(receiveAddress, bordereau);

    if (isRegisterOutputReady(mix)) {
      String mixId = mix.getMixId();
      changeMixStatus(mixId, MixStatus.SIGNING);
    }
    return mix;
  }

  private void logMixStatus(Mix mix) {
    int liquiditiesQueued = mix.getPool().getLiquidityQueue().getSize();
    int mustMixQueued = mix.getPool().getMustMixQueue().getSize();
    log.info(
        "["
            + mix.getMixId()
            + "] "
            + mix.getNbInputsMustMix()
            + "/"
            + mix.getPool().getMinMustMix()
            + " mustMix, "
            + mix.getNbInputsLiquidities()
            + "/"
            + mix.getPool().getMinLiquidity()
            + "+"
            + mix.getSurge()
            + " liquidity, "
            + mix.getNbInputs()
            + "/"
            + mix.getPool().getAnonymitySet()
            + " anonymitySet, "
            + mix.computeMinerFeeAccumulated()
            + "/"
            + mix.getPool().getMinerFeeMix()
            + "sat"
            + ", "
            + mix.getNbConfirmingInputs()
            + " confirming, mixStatus="
            + mix.getMixStatus()
            + " (pool: "
            + liquiditiesQueued
            + " liquidities + "
            + mustMixQueued
            + " mustMixs)");
  }

  protected synchronized boolean isRegisterOutputReady(Mix mix) {
    if (!isConfirmInputReady(mix)) {
      return false;
    }

    return (mix.getReceiveAddresses().size() == mix.getNbInputs());
  }

  protected boolean revalidateInputsForSpent(Mix mix) {
    // check for spent inputs
    List<ConfirmedInput> spentInputs = new ArrayList<>();
    for (ConfirmedInput confirmedInput : mix.getInputs()) {
      TxOutPoint outPoint = confirmedInput.getRegisteredInput().getOutPoint();
      if (!rpcClientService.isTxOutUnspent(outPoint.getHash(), outPoint.getIndex())) {
        // input was spent in meantime
        spentInputs.add(confirmedInput);
      }
    }

    if (spentInputs.isEmpty()) {
      return true; // no input spent => valid
    }

    // remove spent inputs
    for (ConfirmedInput spentInput : spentInputs) {
      log.warn(
          "Found " + spentInputs.size() + " confirmed input(s) spent in meantime!", spentInput);
      mix.unregisterInput(spentInput);
    }
    if (mix.isAlreadyStarted()) {
      blameAndResetMix(mix, spentInputs, BlameReason.SPENT, FailReason.SPENT);
    }
    return false; // not valid
  }

  public synchronized void revealOutput(String mixId, String username, String receiveAddress)
      throws MixException, IllegalInputException {
    Mix mix = getMix(mixId, MixStatus.REVEAL_OUTPUT);

    // verify this username didn't already reveal his output
    if (mix.hasRevealedOutputUsername(username)) {
      log.warn("Rejecting already revealed username: " + username);
      throw new IllegalInputException(
          ServerErrorCode.INPUT_ALREADY_REGISTERED, "Output already revealed");
    }
    // verify this receiveAddress was not already revealed (someone could try to register 2 inputs
    // and reveal same receiveAddress to block mix)
    if (mix.hasRevealedReceiveAddress(receiveAddress)) {
      log.warn("Rejecting already revealed receiveAddress: " + receiveAddress);
      throw new IllegalInputException(
          ServerErrorCode.INPUT_ALREADY_REGISTERED, "ReceiveAddress already revealed");
    }

    // verify an output was registered with this receiveAddress
    if (!mix.getReceiveAddresses().contains(receiveAddress)) {
      throw new IllegalInputException(ServerErrorCode.INVALID_ARGUMENT, "Invalid receiveAddress");
    }

    mix.addRevealedOutput(username, receiveAddress);
    log.info("[" + mixId + "] " + username + " revealed output");

    if (isRevealOutputReady(mix)) {
      blameForRevealOutputAndResetMix(mix);
    }
  }

  protected synchronized boolean isRevealOutputReady(Mix mix) {
    // don't wait for the last one who didn't sign
    return (mix.getNbRevealedOutputs() == mix.getNbInputs() - 1);
  }

  public synchronized void registerSignature(String mixId, String username, String[] witness60)
      throws Exception {
    Mix mix = getMix(mixId, MixStatus.SIGNING);

    // check user
    ConfirmedInput confirmedInput =
        mix.getInputByUsername(username)
            .orElseThrow(
                () ->
                    new IllegalInputException(
                        ServerErrorCode.INPUT_REJECTED,
                        "Input not found for signing username=" + username));
    if (mix.getSignedByUsername(username)) {
      throw new IllegalInputException(
          ServerErrorCode.INPUT_ALREADY_REGISTERED, "User already signed, username=" + username);
    }
    TxOutPoint txOutPoint = confirmedInput.getRegisteredInput().getOutPoint();

    // sign
    Transaction tx = mix.getTx();
    Integer inputIndex = txUtil.findInputIndex(tx, txOutPoint.getHash(), txOutPoint.getIndex());
    TransactionWitness witness = Utils.witnessUnserialize64(witness60);
    tx.setWitness(inputIndex, witness);

    // verify
    try {
      txUtil.verifySignInput(tx, inputIndex, txOutPoint.getValue(), txOutPoint.getScriptBytes());
    } catch (Exception e) {
      log.error("Invalid signature: verifySignInput failed", e);
      throw new IllegalInputException(ServerErrorCode.INVALID_ARGUMENT, "Invalid signature");
    }

    // signature success
    mix.setTx(tx);
    mix.setSignedByUsername(username);
    log.info("[" + mixId + "]  " + username + " registered signature");

    if (isRegisterSignaturesReady(mix)) {
      // check final transaction
      tx.verify();

      log.info("Tx to broadcast: \n" + tx + "\nRaw: " + TxUtil.getInstance().getTxHex(tx));
      try {
        rpcClientService.broadcastTransaction(tx);
        goSuccess(mix);
      } catch (BroadcastException e) {
        log.error("Unable to broadcast tx: ", e);
        goFail(mix, FailReason.FAIL_BROADCAST, e.getFailInfo());
      }
    }
  }

  protected synchronized boolean isRegisterSignaturesReady(Mix mix) {
    if (!isRegisterOutputReady(mix)) {
      return false;
    }
    return (mix.getNbSignatures() == mix.getNbInputs());
  }

  public synchronized void changeMixStatus(String mixId, MixStatus mixStatus) {
    log.info("[MIX " + mixId + "] => " + mixStatus);
    Mix mix = null;
    try {
      mix = getMix(mixId);
      if (mixStatus.equals(mix.getMixStatus())) {
        // just in case...
        log.error(
            "mixStatus inconsistency detected! (already " + mixStatus + ")",
            new IllegalStateException());
        return;
      }

      if (mixStatus == MixStatus.SIGNING) {
        try {
          Transaction tx = computeTransaction(mix);
          mix.setTx(tx);

          log.info("Txid: " + tx.getHashAsString());
          if (log.isDebugEnabled()) {
            log.debug("Tx to sign: \n" + tx + "\nRaw: " + TxUtil.getInstance().getTxHex(tx));
          }
        } catch (Exception e) {
          log.error("Unexpected exception on buildTransaction() for signing", e);
          throw new MixException("System error");
        }
      }

      // update mix status
      mix.setMixStatusAndTime(mixStatus);

      if (mixStatus == MixStatus.REGISTER_OUTPUT) {
        // silently requeue late confirming inputs
        Collection<RegisteredInput> confirmingInputs = mix.clearConfirmingInputs();
        if (log.isDebugEnabled()) {
          log.debug(
              "[MIX "
                  + mixId
                  + "] Requeueing "
                  + confirmingInputs.size()
                  + " late confirming inputs");
        }
        for (RegisteredInput confirmingInput : confirmingInputs) {
          try {
            poolService.registerInput(
                mix.getPool().getPoolId(),
                confirmingInput.getUsername(),
                confirmingInput.isLiquidity(),
                confirmingInput.getOutPoint(),
                confirmingInput.getIp(),
                confirmingInput.getLastUserHash());
          } catch (Exception e) {
            log.error("requeue confirming input failed", e);
          }
        }
      }

      boolean mixOver = (mixStatus == MixStatus.SUCCESS || mixStatus == MixStatus.FAIL);
      // save mix before notifying users
      if (mixOver) {
        saveMixResult(mix, mixStatus);
      }

      mixLimitsService.onMixStatusChange(mix);

      // notify users (ConfirmInputResponse was already sent when user joined mix)
      if (mixStatus != MixStatus.CONFIRM_INPUT) {
        MixStatusNotification mixStatusNotification = computeMixStatusNotification(mixId);
        sendToMixingUsers(mix, mixStatusNotification);
      }

      // start next mix
      if (mixOver) {
        onMixOver(mix);
      }
    } catch (MixException e) {
      log.error("Unexpected mix error", e);
      if (mix != null) {
        onMixOver(mix);
      }
    }
  }

  private void sendToMixingUsers(Mix mix, Object payload) {
    List<String> usernames =
        mix.getInputs()
            .parallelStream()
            .map(confirmedInput -> confirmedInput.getRegisteredInput().getUsername())
            .collect(Collectors.toList());
    WSMessageService.sendPrivate(usernames, payload);
  }

  private MixStatusNotification computeMixStatusNotification(String mixId) throws MixException {
    Mix mix = getMix(mixId);
    MixStatusNotification mixStatusNotification = null;
    switch (mix.getMixStatus()) {
      case REGISTER_OUTPUT:
        String inputsHash = mix.computeInputsHash();
        mixStatusNotification = new RegisterOutputMixStatusNotification(mixId, inputsHash);
        break;
      case REVEAL_OUTPUT:
        mixStatusNotification = new RevealOutputMixStatusNotification(mixId);
        break;
      case SIGNING:
        String tx64 = WhirlpoolProtocol.encodeBytes(mix.getTx().bitcoinSerialize());
        mixStatusNotification = new SigningMixStatusNotification(mixId, tx64);
        break;
      case SUCCESS:
        mixStatusNotification = new SuccessMixStatusNotification(mixId);
        break;
      case FAIL:
        mixStatusNotification = new FailMixStatusNotification(mixId);
        break;
      default:
        log.error("computeMixStatusNotification: unknown MixStatus " + mix.getMixStatus());
        break;
    }
    return mixStatusNotification;
  }

  private Mix getMix(String mixId) throws MixException {
    return getMix(mixId, null);
  }

  private Mix getMix(String mixId, MixStatus mixStatus) throws MixException {
    Mix mix = currentMixs.get(mixId);
    if (mix == null) {
      if (log.isDebugEnabled()) {
        log.debug("Mix not found: " + mixId + ". currentMixs=" + currentMixs.keySet());
      }
      throw new MixException("Mix not found: " + mixId);
    }
    if (mixStatus != null && !mixStatus.equals(mix.getMixStatus())) {
      throw new MixException(
          "Operation not permitted for current mix status: expected="
              + mixStatus
              + ", actual="
              + mix.getMixStatus());
    }
    return mix;
  }

  private Mix getMixByInputsHash(String inputsHash, MixStatus mixStatus)
      throws IllegalInputException, MixException {
    List<Mix> mixsFound =
        currentMixs
            .values()
            .parallelStream()
            .filter(mix -> mix.computeInputsHash().equals(inputsHash))
            .collect(Collectors.toList());
    if (mixsFound.size() != 1) {
      log.warn("REGISTER_OUTPUT rejected: no current mix for inputsHash=" + inputsHash);
      // reject with generic message because we may not be responsible of this error (ie: another
      // client disconnected during the mix)
      throw new MixException("Mix failed");
    }
    Mix mix = mixsFound.get(0);
    if (mixStatus != null && !mixStatus.equals(mix.getMixStatus())) {
      throw new MixException(
          "Operation not permitted for current mix status: expected="
              + mixStatus
              + ", actual="
              + mix.getMixStatus());
    }
    return mix;
  }

  private Transaction computeTransaction(Mix mix) throws Exception {
    NetworkParameters params = cryptoService.getNetworkParameters();
    Transaction tx = new Transaction(params);
    List<TransactionInput> inputs = new ArrayList<>();
    List<TransactionOutput> outputs = new ArrayList<>();

    tx.clearOutputs();
    for (String receiveAddress : mix.getReceiveAddresses()) {
      TransactionOutput txOutSpend =
          bech32Util.getTransactionOutput(receiveAddress, mix.getPool().getDenomination(), params);
      outputs.add(txOutSpend);
    }

    //
    // BIP69 sort outputs
    //
    Collections.sort(outputs, new BIP69OutputComparator());
    for (TransactionOutput to : outputs) {
      tx.addOutput(to);
    }

    //
    // create 1 mix tx
    //
    for (ConfirmedInput confirmedInput : mix.getInputs()) {
      RegisteredInput registeredInput = confirmedInput.getRegisteredInput();
      // send from bech32 input
      long spendAmount = registeredInput.getOutPoint().getValue();
      TxOutPoint registeredOutPoint = registeredInput.getOutPoint();
      TransactionOutPoint outPoint =
          new TransactionOutPoint(
              params,
              registeredOutPoint.getIndex(),
              Sha256Hash.wrap(registeredOutPoint.getHash()),
              Coin.valueOf(spendAmount));
      TransactionInput txInput =
          new TransactionInput(params, null, new byte[] {}, outPoint, Coin.valueOf(spendAmount));
      inputs.add(txInput);
    }

    //
    // BIP69 sort inputs
    //
    Collections.sort(inputs, new BIP69InputComparator());
    for (TransactionInput ti : inputs) {
      tx.addInput(ti);
    }
    return tx;
  }

  public void onTimeoutRegisterOutput(Mix mix) {
    log.info("[" + mix.getMixId() + "] REGISTER_OUTPUT time over, mix failed.");
    if (mix.getReceiveAddresses().isEmpty()) {
      // no output registered at all => no legit user suffered, skip REVEAL_OUTPUT and immediately
      // restart round
      goFail(mix, FailReason.FAIL_REGISTER_OUTPUTS, null);
    } else {
      // we have legit output registered => go REVEAL_OUTPUT to blame the others
      changeMixStatus(mix.getMixId(), MixStatus.REVEAL_OUTPUT);
    }
  }

  public void onTimeoutRevealOutput(Mix mix) {
    blameForRevealOutputAndResetMix(mix);
  }

  private void blameForRevealOutputAndResetMix(Mix mix) {
    // blame users who didn't register outputs
    List<ConfirmedInput> confirmedInputsToBlame = mix.getInputsNotRevealedOutput();
    log.info(
        "["
            + mix.getMixId()
            + "] REVEAL_OUTPUT time over, mix failed. Blaming "
            + confirmedInputsToBlame.size()
            + " who didn't sign...");
    blameAndResetMix(
        mix, confirmedInputsToBlame, BlameReason.REGISTER_OUTPUT, FailReason.FAIL_REGISTER_OUTPUTS);
  }

  public void onTimeoutSigning(Mix mix) {
    // blame users who didn't sign
    List<ConfirmedInput> confirmedInputsToBlame = mix.getInputsNotSigned();
    log.info(
        "["
            + mix.getMixId()
            + "] SIGNING time over, mix failed. Blaming "
            + confirmedInputsToBlame.size()
            + " who didn't sign...");
    blameAndResetMix(mix, confirmedInputsToBlame, BlameReason.SIGNING, FailReason.FAIL_SIGNING);
  }

  public void blameAndResetMix(
      Mix mix, List<ConfirmedInput> spentInputs, BlameReason blameReason, FailReason failReason) {
    // blame inputs
    for (ConfirmedInput spentInput : spentInputs) {
      blameService.blame(spentInput.getRegisteredInput(), blameReason, mix);
    }

    // reset mix
    String outpointKeysToBlame = computeOutpointKeysToBlame(spentInputs);
    goFail(mix, failReason, outpointKeysToBlame);
  }

  private String computeOutpointKeysToBlame(Collection<ConfirmedInput> confirmedInputsToBlame) {
    List<String> outpointKeysToBlame = new ArrayList<>();
    for (ConfirmedInput confirmedInputToBlame : confirmedInputsToBlame) {
      outpointKeysToBlame.add(confirmedInputToBlame.getRegisteredInput().getOutPoint().toKey());
    }
    String outpointKeysToBlameStr = StringUtils.join(outpointKeysToBlame, ";");
    return outpointKeysToBlameStr;
  }

  public synchronized void goFail(Mix mix, FailReason failReason, String failInfo) {
    if (MixStatus.FAIL.equals(mix.getMixStatus())) {
      // may happen when multiple inputs disconnect simultaneously
      log.info("Ignoring goFail(): mix already failed");
      return;
    }

    // clear failed mix outputs
    log.warn("Mix failed: " + failReason.name() + (failInfo != null ? ", " + failInfo : ""));
    for (String mixOutput : mix.getReceiveAddresses()) {
      dbService.deleteMixOutput(mixOutput);
    }

    mix.setFailReason(failReason);
    mix.setFailInfo(failInfo);
    changeMixStatus(mix.getMixId(), MixStatus.FAIL);
  }

  public void goSuccess(Mix mix) {
    changeMixStatus(mix.getMixId(), MixStatus.SUCCESS);
  }

  private Optional<ConfirmedInput> unregister(Mix mix, String username) {
    if (MixStatus.FAIL.equals(mix.getMixStatus())) {
      return Optional.empty();
    }

    // remove from confirming inputs
    mix.removeConfirmingInputByUsername(username);

    // remove from confirmed input
    Optional<ConfirmedInput> confirmedInputOpt = mix.getInputByUsername(username);
    if (confirmedInputOpt.isPresent()) {
      ConfirmedInput confirmedInput = confirmedInputOpt.get();
      mix.unregisterInput(confirmedInput);
    }
    return confirmedInputOpt;
  }

  protected void onClientDisconnect(String username) {
    for (Mix mix : getCurrentMixs()) {
      String lastReceiveAddressRejected = mix.getLastReceiveAddressesRejected();
      Optional<ConfirmedInput> unregisteredInputOpt = unregister(mix, username);
      if (unregisteredInputOpt.isPresent()) {
        ConfirmedInput confirmedInput = unregisteredInputOpt.get();

        if (mix.isAlreadyStarted()) {
          // blame
          BlameReason blameReason = BlameReason.DISCONNECT;
          blameService.blame(
              confirmedInput.getRegisteredInput(), blameReason, mix, lastReceiveAddressRejected);

          // restart mix
          String failInfo = computeOutpointKeysToBlame(Arrays.asList(confirmedInput));
          FailReason failReason = FailReason.DISCONNECT;
          if (lastReceiveAddressRejected != null) {
            failInfo += " " + lastReceiveAddressRejected;
          }
          goFail(mix, failReason, failInfo);
        } else {
          // mix can continue
          if (mix.getSurge() > 0) {
            // adjust surge limit on mustmix disconnect
            if (!confirmedInput.getRegisteredInput().isLiquidity()) {
              // adjust surge which may be lowered down
              mix.setSurge();
              int excessLiquidities = mix.getExessLiquidityAndSurge();
              if (excessLiquidities > 0) {
                log.info(
                    "Disconnected mustmix caused surge down adjustment => disconnecting "
                        + excessLiquidities
                        + " excess liquidities");
                mix.unregisterInputLiquidities(excessLiquidities);
                // TODO disconnect client ?
              }
            }
          }
        }
      }
    }
  }

  private Collection<Mix> getCurrentMixs() {
    return currentMixs.values();
  }

  public void __reset() {
    currentMixs = new ConcurrentHashMap<>();
    mixLimitsService.__reset();
    poolService
        .getPools()
        .forEach(
            pool -> {
              __nextMix(pool);
            });
  }

  public Mix __nextMix(Pool pool) {
    String mixId = Utils.generateUniqueString();
    Mix mix = new Mix(mixId, pool, cryptoService);
    startMix(mix);
    return mix;
  }

  private void saveMixResult(Mix mix, MixStatus mixStatus) {
    // save in database
    try {
      dbService.saveMix(mix);
    } catch (Exception e) {
      log.error("", e);
    }

    // export to CSV
    try {
      MixCsv mixCsv = exportService.exportMix(mix);
      metricService.onMixResult(mixCsv, mix.getInputs());
    } catch (Exception e) {
      log.error("", e);
    }

    if (mixStatus == MixStatus.SUCCESS) {
      // save mix txid
      try {
        dbService.saveMixTxid(mix.getTx().getHashAsString(), mix.getPool().getDenomination());
      } catch (Exception e) {
        log.error("", e);
      }
    }
  }

  private void onMixOver(Mix mix) {
    // unmanage
    try {
      mixLimitsService.unmanage(mix);
    } catch (Exception e) {
      log.error("", e);
    }

    // reset lastUserHash
    poolService.resetLastUserHash(mix);

    // start new mix
    __nextMix(mix.getPool());
  }

  private synchronized void startMix(Mix mix) {
    Pool pool = mix.getPool();
    Mix currentMix = pool.getCurrentMix();
    if (currentMix != null) {
      currentMixs.remove(currentMix.getMixId());
      // TODO disconnect all clients (except liquidities?)
    }

    String mixId = mix.getMixId();
    currentMixs.put(mixId, mix);
    pool.setCurrentMix(mix);

    log.info("[" + pool.getPoolId() + "][NEW MIX " + mix.getMixId() + "]");
    logMixStatus(mix);
    mixLimitsService.manage(mix);
  }

  public Predicate<Map.Entry<String, RegisteredInput>> computeFilterInputMixable(Mix mix) {
    return entry -> {
      RegisteredInput registeredInput = entry.getValue();
      try {
        validateForConfirmInput(mix, registeredInput);
        return true; // mixable
      } catch (Exception e) {
        return false; // not mixable
      }
    };
  }

  public MixLimitsService __getMixLimitsService() {
    return mixLimitsService;
  }

  public static void __setCONFIRM_INPUT_CHECK_DELAY(int confirmInputCheckDelay) {
    CONFIRM_INPUT_CHECK_DELAY = confirmInputCheckDelay;
  }
}
