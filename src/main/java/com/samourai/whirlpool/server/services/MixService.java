package com.samourai.whirlpool.server.services;

import com.samourai.wallet.bip69.BIP69InputComparator;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.client.wallet.WhirlpoolEventService;
import com.samourai.whirlpool.protocol.WhirlpoolErrorCode;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.soroban.WhirlpoolApiClient;
import com.samourai.whirlpool.protocol.soroban.WhirlpoolApiCoordinator;
import com.samourai.whirlpool.protocol.soroban.payload.beans.BlameReason;
import com.samourai.whirlpool.protocol.soroban.payload.mix.*;
import com.samourai.whirlpool.protocol.soroban.payload.registerInput.RegisterInputResponse;
import com.samourai.whirlpool.protocol.websocket.notifications.*;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.beans.MixStatus;
import com.samourai.whirlpool.server.beans.event.MixProgressEvent;
import com.samourai.whirlpool.server.beans.event.MixStartEvent;
import com.samourai.whirlpool.server.beans.event.MixStopEvent;
import com.samourai.whirlpool.server.beans.export.MixCsv;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.BroadcastException;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MixService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final AsyncUtil asyncUtil = AsyncUtil.getInstance();
  private WSMessageService WSMessageService;
  private CryptoService cryptoService;
  private BlameService blameService;
  private DbService dbService;
  private RpcClientService rpcClientService;
  private BackendService backendService;
  private MixLimitsService mixLimitsService;
  private Bech32UtilGeneric bech32Util;
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private PoolService poolService;
  private ExportService exportService;
  private MetricService metricService;
  private TaskService taskService;
  private WhirlpoolApiCoordinator whirlpoolApiCoordinator;
  private RegisterInputService registerInputService;

  private Map<String, Mix> currentMixs;

  private static final int INVITE_INPUT_DELAY = 2000;

  @Autowired
  public MixService(
      CryptoService cryptoService,
      BlameService blameService,
      DbService dbService,
      RpcClientService rpcClientService,
      BackendService backendService,
      WSMessageService WSMessageService,
      Bech32UtilGeneric bech32Util,
      WhirlpoolServerConfig whirlpoolServerConfig,
      MixLimitsService mixLimitsService,
      PoolService poolService,
      ExportService exportService,
      MetricService metricService,
      TaskService taskService,
      WSSessionService wsSessionService,
      WhirlpoolApiCoordinator whirlpoolApiCoordinator,
      RegisterInputService registerInputService) {
    this.cryptoService = cryptoService;
    this.blameService = blameService;
    this.dbService = dbService;
    this.rpcClientService = rpcClientService;
    this.backendService = backendService;
    this.WSMessageService = WSMessageService;
    this.bech32Util = bech32Util;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    mixLimitsService.setMixService(this); // avoids circular reference
    this.mixLimitsService = mixLimitsService;
    this.poolService = poolService;
    this.exportService = exportService;
    this.metricService = metricService;
    this.taskService = taskService;
    this.whirlpoolApiCoordinator = whirlpoolApiCoordinator;
    this.registerInputService = registerInputService;

    this.__reset();

    // listen websocket onDisconnect
    wsSessionService.addOnDisconnectListener(username -> onClientDisconnect(username));
  }

  /** Last input validations when adding it to a mix (not when queueing it) */
  public void validateOnConfirmInput(Mix mix, RegisteredInput registeredInput)
      throws QueueInputException, IllegalInputException {
    Pool pool = mix.getPool();

    // failMode
    try {
      whirlpoolServerConfig.checkFailMode(FailMode.CONFIRM_INPUT_BLAME);
    } catch (Exception e) {
      blameService.blame(registeredInput, BlameReason.DISCONNECT, mix);
      throw new IllegalInputException(WhirlpoolErrorCode.INPUT_REJECTED, e.getMessage());
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
    String userHashOrNull = registeredInput.getLastUserHash();
    if (userHashOrNull != null) {
      long countInputSameUserHash =
          mix.getInputs()._getInputs().parallelStream()
              .filter(input -> userHashOrNull.equals(input.getLastUserHash()))
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
        mix.getInputs()._getInputs().parallelStream()
            .filter(input -> input.getOutPoint().getHash().equals(inputHash))
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
    if (mix.getInputs().findByAddress(inputAddress).isPresent()) {
      throw new QueueInputException(
          "Current mix is full for inputs with same address", registeredInput, pool.getPoolId());
    }

    // verify same utxo not already confirmed
    if (mix.getInputs().findByOutPoint(registeredInput.getOutPoint()).isPresent()) {
      // input already confirmed => reject duplicate client
      throw new IllegalInputException(
          WhirlpoolErrorCode.INPUT_ALREADY_REGISTERED, "Input already confirmed");
    }
  }

  public void validateForConfirmInput(Mix mix, RegisteredInput registeredInput)
      throws QueueInputException, IllegalInputException {
    validateOnConfirmInput(mix, registeredInput);
  }

  public void disconnectSorobanInputsExpired(Mix mix) {
    // disconnect expired soroban inputs
    long minLastSeen = System.currentTimeMillis() - WhirlpoolApiClient.TIMEOUT_MIX_STATUS;
    Collection<RegisteredInput> sorobanInputsExpired =
        mix.getInputs().getListSorobanInputsExpired(minLastSeen);
    for (RegisteredInput registeredInput : sorobanInputsExpired) {
      onMixInputDisconnect(registeredInput, mix);
    }
  }

  public void onTimeoutConfirmInput(Mix mix) {
    long minConfirmingSince =
        System.currentTimeMillis()
            - com.samourai.whirlpool.protocol.soroban.payload.beans.MixStatus.CONFIRM_INPUT
                .getTimeoutMs();
    mix.cleanConfirmingInputs(minConfirmingSince);

    if (MixStatus.CONFIRM_INPUT.equals(mix.getMixStatus()) && isConfirmInputReady(mix)) {
      // all inputs confirmed
      if (mix.getSurge() > 0 && !mix.isFullWithSurge() && !mix.isConfirmingSurge()) {
        // wait one more cycle to invite & confirm surges
        mix.setConfirmingSurge(true);
      } else {
        // mix is full (or we waited one more cycle to confirm surges) => REGISTER_OUTPUT
        changeMixStatus(mix.getMixId(), MixStatus.REGISTER_OUTPUT);
        return;
      }
    }

    // invite more inputs
    inviteToMix(mix);
  }

  private synchronized int inviteToMix(Mix mix) {
    int liquiditiesInvited = 0, mustMixsInvited = 0;

    // invite liquidities first (to allow concurrent liquidity remixing)
    int liquiditiesToAdd = mix.getAvailableSlotsLiquidityAndSurge();
    if (liquiditiesToAdd > 0) {
      liquiditiesInvited = inviteToMix(mix, true, liquiditiesToAdd);
    }

    // invite mustMixs
    int mustMixsToAdd = mix.getAvailableSlotsMustMix();
    if (mustMixsToAdd > 0) {
      mustMixsInvited = inviteToMix(mix, false, mustMixsToAdd);
    }

    int inputsInvited = liquiditiesInvited + mustMixsInvited;
    if (inputsInvited > 0) {
      log.info(
          "MIX_INVITE_INPUTS "
              + mix.getMixId()
              + " "
              + liquiditiesInvited
              + "/"
              + liquiditiesToAdd
              + " "
              + "liquidities + "
              + mustMixsInvited
              + "/"
              + mustMixsToAdd
              + " mustMixs (anonymitySet: "
              + mix.getNbInputs()
              + "/"
              + mix.getAnonymitySetWithSurge()
              + ")");
    }
    return inputsInvited;
  }

  private int inviteToMix(Mix mix, boolean liquidity, int maxInvites) {
    maxInvites += 1; // invite 1 more just in case someone doesn't reply

    Predicate<RegisteredInput> filterInputMixable =
        registeredInput -> {
          if (registeredInput.isQuarantine()) {
            return false; // not mixable
          }
          if (mix.hasConfirmingInput(registeredInput)) {
            return false; // already invited (wait for confirming expiration before reinviting)
          }
          try {
            validateForConfirmInput(mix, registeredInput);
            return true; // mixable
          } catch (Exception e) { // including QueueInputException
            String dateStr = DateFormat.getDateTimeInstance().format(new Date());
            registeredInput.setQuarantineReason(dateStr + ": " + e.getMessage());
            return false; // not mixable
          }
        };

    InputPoolQueue queue =
        (liquidity ? mix.getPool().getLiquidityQueue() : mix.getPool().getMustMixQueue());
    try {
      queue.refreshSorobanInputs();
    } catch (Exception e) {
      log.error("error refreshing soroban inputs", e);
    }
    if (!queue.hasInputsClassicOrSoroban()) {
      return 0;
    }

    int nbInvited = 0;
    while (true) {
      // stop when enough invites
      if (nbInvited >= maxInvites) {
        break;
      }

      // invite
      try {
        Optional<RegisteredInput> registeredInputOpt =
            queue.removeRandomClassicOrSoroban(filterInputMixable, registerInputService);
        if (!registeredInputOpt.isPresent()) {
          // stop when no more input to invite
          break;
        }

        RegisteredInput registeredInput = registeredInputOpt.get();
        inviteToMix(mix, registeredInput);
        nbInvited++;
      } catch (Exception e) {
        log.error("inviteToMix failed", e);
      }
    }
    return nbInvited;
  }

  private void inviteToMix(Mix mix, RegisteredInput registeredInput) throws Exception {
    // register confirming input
    mix.registerConfirmingInput(registeredInput);

    if (registeredInput.isSoroban()) {
      // invite soroban input
      log.info("MIX_INVITE_INPUT_SOROBAN " + mix.getMixId() + " " + registeredInput);
      RegisterInputResponse registerInputResponse =
          new RegisterInputResponse(mix.getMixId(), mix.getPublicKey());
      asyncUtil.blockingAwait(
          whirlpoolApiCoordinator
              .getRpcSession()
              .withSorobanClient(
                  sorobanClient ->
                      registeredInput
                          .getSorobanInput()
                          .getEndpointRegisterInputReply()
                          .send(sorobanClient, registerInputResponse)));
    } else {
      // invite classic input
      log.info("MIX_INVITE_INPUT_CLASSIC " + mix.getMixId() + " " + registeredInput);
      // add delay as we are called from LimitsWatcher which may run just after an input registered
      String publicKey64 = WhirlpoolProtocol.encodeBytes(mix.getPublicKey());
      ConfirmInputMixStatusNotification confirmInputMixStatusNotification =
          new ConfirmInputMixStatusNotification(mix.getMixId(), publicKey64);
      taskService.runOnce(
          INVITE_INPUT_DELAY,
          () -> {
            // send invite to mix
            WSMessageService.sendPrivate(
                registeredInput.getUsername(), confirmInputMixStatusNotification);
          });
    }
  }

  protected boolean isConfirmInputReady(Mix mix) {
    if (!whirlpoolServerConfig.isMixEnabled()) {
      return false;
    }
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

  public void registerOutputFailure(Mix mix, String receiveAddress) {
    mix.setLastReceiveAddressesRejected(receiveAddress);
    log.info(
        "FAIL_REGISTER_OUTPUT " + mix.getMixId() + " registered output failure: " + receiveAddress);
  }

  public void logMixStatus(Mix mix) {
    log.info("// MIX_STATUS " + mix.getMixId() + " " + mix.getLogStatus());
  }

  protected boolean isRegisterOutputReady(Mix mix) {
    if (!isConfirmInputReady(mix)) {
      return false;
    }

    return (mix.getReceiveAddresses().size() == mix.getNbInputs());
  }

  protected boolean revalidateInputsForSpent(Mix mix) {
    // check for spent inputs
    List<RegisteredInput> spentInputs =
        mix.getInputs()._getInputs().parallelStream()
            .filter(
                registeredInput -> {
                  TxOutPoint outPoint = registeredInput.getOutPoint();
                  // input was spent in meantime
                  return !rpcClientService.isTxOutUnspent(outPoint.getHash(), outPoint.getIndex());
                })
            .collect(Collectors.toList());

    if (spentInputs.isEmpty()) {
      return true; // no input spent => valid
    }

    // remove spent inputs
    for (RegisteredInput spentInput : spentInputs) {
      log.warn(
          "Found " + spentInputs.size() + " confirmed input(s) spent in meantime!", spentInput);
      mix.unregisterInput(spentInput);
    }
    if (mix.isBlamableStatus()) {
      blameAndResetMix(mix, spentInputs, BlameReason.SPENT, FailReason.SPENT);
    }
    return false; // not valid
  }

  protected boolean isRevealOutputReady(Mix mix) {
    // don't wait for the last one who didn't sign
    return (mix.getNbRevealedOutputs() == mix.getNbInputs() - 1);
  }

  protected boolean isRegisterSignaturesReady(Mix mix) {
    if (!isRegisterOutputReady(mix)) {
      return false;
    }
    return (mix.getNbSignatures() == mix.getNbInputs());
  }

  public synchronized void changeMixStatus(String mixId, MixStatus mixStatus) {
    Mix mix = null;
    try {
      mix = getMix(mixId);
      log.info("MIX_STATUS_CHANGE " + mix.getMixId() + " ------------> " + mixStatus);
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
      logMixStatus(mix);

      if (mixStatus == MixStatus.REGISTER_OUTPUT) {
        // ignore confirming inputs (they are still in pool queue)
        Collection<RegisteredInput> confirmingInputs = mix.clearConfirmingInputs();
        if (log.isDebugEnabled()) {
          log.debug(
              "MIX "
                  + mix.getMixId()
                  + " Ignoring "
                  + confirmingInputs.size()
                  + " late confirming inputs");
        }
      }

      boolean mixOver = (mixStatus == MixStatus.SUCCESS || mixStatus == MixStatus.FAIL);
      // save mix before notifying users
      if (mixOver) {
        saveMixResult(mix, mixStatus);
      }

      WhirlpoolEventService.getInstance().post(new MixProgressEvent(mix));
      mixLimitsService.onMixStatusChange(mix);

      AbstractMixStatusResponse mixStatusNotificationSoroban = computeMixStatusResponseSoroban(mix);

      // notify users (ConfirmInputResponse was already sent when user joined mix)
      if (mixStatus != MixStatus.CONFIRM_INPUT) {
        sendMixStatusNotificationV0(mix);
      }

      // update mixStatusResponse for soroban users
      mix.setMixStatusResponse(mixStatusNotificationSoroban);

      // start next mix
      if (mixOver) {
        try {
          // send mixResult to soroban
          asyncUtil.blockingAwait(
              whirlpoolApiCoordinator.mixResultSend(mixId, mixStatusNotificationSoroban));
        } catch (Exception e) {
          log.error("mixResultSend failed", e);
        }
        onMixOver(mix);
      }
    } catch (MixException e) {
      log.error("Unexpected mix error", e);
      if (mix != null) {
        onMixOver(mix);
      }
    }
  }

  private void sendMixStatusNotificationV0(Mix mix) {
    MixStatusNotification mixStatusNotificationV0 = computeMixStatusNotificationV0(mix);
    List<String> usernames =
        mix.getInputs().getListBySoroban(false).parallelStream()
            .map(confirmedInput -> confirmedInput.getUsername())
            .collect(Collectors.toList());
    WSMessageService.sendPrivate(usernames, mixStatusNotificationV0);
  }

  private MixStatusNotification computeMixStatusNotificationV0(Mix mix) {
    String mixId = mix.getMixId();
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
        return null;
    }
    return mixStatusNotification;
  }

  private AbstractMixStatusResponse computeMixStatusResponseSoroban(Mix mix) {
    switch (mix.getMixStatus()) {
      case REGISTER_OUTPUT:
        String inputsHash = mix.computeInputsHash();
        return new MixStatusResponseRegisterOutput(inputsHash);
      case REVEAL_OUTPUT:
        return new MixStatusResponseRevealOutput();
      case SIGNING:
        String tx64 = WhirlpoolProtocol.encodeBytes(mix.getTx().bitcoinSerialize());
        return new MixStatusResponseSigning(tx64);
      case SUCCESS:
        String txid = mix.getTx().getHashAsString();
        return new MixStatusResponseSuccess(txid);
      case FAIL:
        return new MixStatusResponseFail();
      default:
        log.error("computeMixStatusNotification: unknown MixStatus " + mix.getMixStatus());
        return null;
    }
  }

  public Mix getMix(String mixId) throws MixException {
    return getMix(mixId, null);
  }

  public Mix getMix(String mixId, MixStatus mixStatus) throws MixException {
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

  public Mix getMixByInputsHash(String inputsHash, MixStatus mixStatus) throws MixException {
    List<Mix> mixsFound =
        currentMixs.values().parallelStream()
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
    for (RegisteredInput input : mix.getInputs()._getInputs()) {
      // send from bech32 input
      long spendAmount = input.getOutPoint().getValue();
      TxOutPoint registeredOutPoint = input.getOutPoint();
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
    log.info("MIX_FAILED " + mix.getMixId() + " REGISTER_OUTPUT time over, mix failed.");
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
    List<RegisteredInput> confirmedInputsToBlame = mix.getInputsNotRevealedOutput();
    log.info(
        "MIX_FAILED "
            + mix.getMixId()
            + " REVEAL_OUTPUT time over, mix failed. Blaming "
            + confirmedInputsToBlame.size()
            + " who didn't sign...");
    blameAndResetMix(
        mix, confirmedInputsToBlame, BlameReason.REGISTER_OUTPUT, FailReason.FAIL_REGISTER_OUTPUTS);
  }

  public void onTimeoutSigning(Mix mix) {
    // blame users who didn't sign
    List<RegisteredInput> confirmedInputsToBlame = mix.getInputsNotSigned();
    log.info(
        "MIX_FAILED "
            + mix.getMixId()
            + " SIGNING time over, mix failed. Blaming "
            + confirmedInputsToBlame.size()
            + " who didn't sign...");
    blameAndResetMix(mix, confirmedInputsToBlame, BlameReason.SIGNING, FailReason.FAIL_SIGNING);
  }

  public void onRegisterOutput(Mix mix) throws Exception {
    if (isRegisterOutputReady(mix)) {
      String mixId = mix.getMixId();
      changeMixStatus(mixId, MixStatus.SIGNING);
    }
  }

  public void onSign(Mix mix) throws Exception {
    if (isRegisterSignaturesReady(mix)) {
      // check final transaction
      Transaction tx = mix.getTx();
      tx.verify();

      log.info("Tx to broadcast: \n" + tx + "\nRaw: " + TxUtil.getInstance().getTxHex(tx));
      try {
        String txHex = TxUtil.getInstance().getTxHex(tx);
        List<Integer> strictModeVouts =
            IntStream.range(0, tx.getOutputs().size()).boxed().collect(Collectors.toList());
        backendService.pushTx(txHex, strictModeVouts);
        goSuccess(mix);
      } catch (BroadcastException e) {
        log.error("Unable to broadcast tx: ", e);
        goFail(mix, FailReason.FAIL_BROADCAST, e.getFailInfo());
      }
    }
  }

  public void onRevealOutput(Mix mix) {
    if (isRevealOutputReady(mix)) {
      blameForRevealOutputAndResetMix(mix);
    }
  }

  public void blameAndResetMix(
      Mix mix, List<RegisteredInput> blameInputs, BlameReason blameReason, FailReason failReason) {
    // blame inputs
    for (RegisteredInput spentInput : blameInputs) {
      blameService.blame(spentInput, blameReason, mix);
    }
    mix.setBlameInputs(blameInputs, blameReason);

    // reset mix
    String failInfo = computeFailInfoBlame(blameInputs);
    goFail(mix, failReason, failInfo);
  }

  private String computeFailInfoBlame(Collection<RegisteredInput> confirmedInputsToBlame) {
    List<String> outpointKeysToBlame = new ArrayList<>();
    for (RegisteredInput confirmedInputToBlame : confirmedInputsToBlame) {
      String inputType = confirmedInputToBlame.getTypeStr();
      outpointKeysToBlame.add(confirmedInputToBlame.getOutPoint().toKey() + "(" + inputType + ")");
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
    log.warn("Mix failed: " + failReason.name() + (failInfo != null ? ", " + failInfo : ""));

    mix.setFailReason(failReason);
    mix.setFailInfo(failInfo);
    changeMixStatus(mix.getMixId(), MixStatus.FAIL);
  }

  public void goSuccess(Mix mix) {
    changeMixStatus(mix.getMixId(), MixStatus.SUCCESS);
  }

  protected void onClientDisconnect(String username) {
    for (Mix mix : getCurrentMixs()) {
      if (!mix.isDone()) {
        // remove from confirming inputs
        mix.removeConfirmingInputByUsername(username);

        // was input already confirmed?
        Optional<RegisteredInput> unregisterInputOpt = mix.getInputs().findByUsername(username);
        if (unregisterInputOpt.isPresent()) {
          onMixInputDisconnect(unregisterInputOpt.get(), mix);
        }
      }
    }
  }

  private void onMixInputDisconnect(RegisteredInput confirmedInput, Mix mix) {
    log.info("MIX_INPUT_DISCONNECT_"+confirmedInput.getTypeStr()+" " + mix.getMixId() + " " + confirmedInput);
    if (mix.isBlamableStatus()) {
      // don't unregister input to preserve original mix metrics

      // blame
      BlameReason blameReason = BlameReason.DISCONNECT;
      String lastReceiveAddressRejected = mix.getLastReceiveAddressesRejected();
      blameService.blame(confirmedInput, blameReason, mix, lastReceiveAddressRejected);

      // restart mix
      String failInfo = computeFailInfoBlame(Arrays.asList(confirmedInput));
      FailReason failReason = FailReason.DISCONNECT;
      if (lastReceiveAddressRejected != null) {
        failInfo += " " + lastReceiveAddressRejected;
      }
      goFail(mix, failReason, failInfo);
    } else {

      // remove from confirmed input
      mix.unregisterInput(confirmedInput);

      // invalidate quarantine to recompute it
      mix.getPool().clearQuarantine();

      // mix can continue
      if (mix.getSurge() > 0) {
        // adjust surge limit on mustmix disconnect
        if (!confirmedInput.isLiquidity()) {
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
    String mixId = pool.getPoolId() + "-" + Utils.generateUniqueString();
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
      metricService.onMixResult(mixCsv, mix.getInputs()._getInputs());
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
    WhirlpoolEventService.getInstance().post(new MixStopEvent(mix));

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
    pool.clearQuarantine();

    String mixId = mix.getMixId();
    currentMixs.put(mixId, mix);
    pool.setCurrentMix(mix);

    log.info("MIX_START " + mix.getMixId() + " ************************");
    logMixStatus(mix);
    mixLimitsService.manage(mix);
    WhirlpoolEventService.getInstance().post(new MixStartEvent(mix));
  }

  public MixLimitsService __getMixLimitsService() {
    return mixLimitsService;
  }
}
