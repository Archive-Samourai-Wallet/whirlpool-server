package com.samourai.whirlpool.server.services.soroban;

import com.samourai.soroban.client.RpcWallet;
import com.samourai.soroban.client.meeting.SorobanMessageWithSender;
import com.samourai.soroban.client.rpc.RpcClient;
import com.samourai.soroban.client.rpc.RpcClientEncrypted;
import com.samourai.soroban.client.rpc.RpcMode;
import com.samourai.wallet.bip47.BIP47UtilGeneric;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.wallet.util.JSONUtils;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolNetwork;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.CoordinatorInfo;
import com.samourai.whirlpool.protocol.rest.PoolInfoSoroban;
import com.samourai.whirlpool.protocol.soroban.ErrorSorobanMessage;
import com.samourai.whirlpool.protocol.soroban.InviteMixSorobanMessage;
import com.samourai.whirlpool.protocol.soroban.RegisterCoordinatorSorobanMessage;
import com.samourai.whirlpool.protocol.soroban.RegisterInputSorobanMessage;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisterInputSoroban;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.ServerErrorCode;
import com.samourai.whirlpool.server.utils.Utils;
import io.reactivex.Completable;
import io.reactivex.Single;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SorobanCoordinatorApi {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final AsyncUtil asyncUtil = AsyncUtil.getInstance();
  private static final JSONUtils jsonUtil = JSONUtils.getInstance();
  private static final BIP47UtilGeneric bip47Util = Bip47UtilJava.getInstance();
  private WhirlpoolNetwork whirlpoolNetwork;

  public SorobanCoordinatorApi(WhirlpoolServerConfig serverConfig) {
    this.whirlpoolNetwork = serverConfig.getWhirlpoolNetwork();
  }

  public Completable registerCoordinator(
      RpcClientEncrypted rpcClient,
      String coordinatorId,
      String urlClear,
      String urlOnion,
      Collection<PoolInfoSoroban> poolInfos)
      throws Exception {
    String directory = WhirlpoolProtocol.getSorobanDirCoordinators(whirlpoolNetwork);
    CoordinatorInfo coordinatorInfo = new CoordinatorInfo(coordinatorId, urlClear, urlOnion);
    RegisterCoordinatorSorobanMessage message =
        new RegisterCoordinatorSorobanMessage(coordinatorInfo, poolInfos);
    return rpcClient.directoryAdd(directory, message.toPayload(), RpcMode.SHORT);
  }

  public Single<String> sendError(
      RpcClientEncrypted rpcClient,
      RegisterInputSoroban registerInputSoroban,
      RpcWallet rpcWalletCoordinator,
      String message)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("sendError: " + message);
    }
    ErrorSorobanMessage errorSorobanMessage =
        new ErrorSorobanMessage(ServerErrorCode.INPUT_REJECTED, message);

    PaymentCode paymentCodeClient = registerInputSoroban.getSorobanPaymentCode();
    NetworkParameters params = rpcWalletCoordinator.getBip47Wallet().getParams();
    String directory =
        WhirlpoolProtocol.getSorobanDirRegisterInputResponse(
            rpcWalletCoordinator,
            whirlpoolNetwork,
            paymentCodeClient,
            registerInputSoroban.getSorobanMessage().utxoHash,
            registerInputSoroban.getSorobanMessage().utxoIndex,
            bip47Util,
            params);
    return rpcClient.sendEncrypted(
        directory, errorSorobanMessage.toPayload(), paymentCodeClient, RpcMode.SHORT);
  }

  public Single<String> inviteToMix(
      RpcClientEncrypted rpcClient,
      RegisteredInput registeredInput,
      Mix mix,
      String coordinatorUrlClear,
      String coordinatorUrlOnion,
      RpcWallet rpcWalletCoordinator)
      throws Exception {
    InviteMixSorobanMessage inviteMixSorobanMessage =
        new InviteMixSorobanMessage(
            mix.getMixId(), mix.getPublicKey(), coordinatorUrlClear, coordinatorUrlOnion);

    PaymentCode paymentCodeClient = registeredInput.getSorobanPaymentCode();
    NetworkParameters params = rpcWalletCoordinator.getBip47Wallet().getParams();
    String directory =
        WhirlpoolProtocol.getSorobanDirRegisterInputResponse(
            rpcWalletCoordinator,
            whirlpoolNetwork,
            paymentCodeClient,
            registeredInput.getOutPoint().getHash(),
            registeredInput.getOutPoint().getIndex(),
            bip47Util,
            params);
    return rpcClient.sendEncrypted(
        directory, inviteMixSorobanMessage.toPayload(), paymentCodeClient, RpcMode.SHORT);
  }

  public Completable unregisterInput(
      RpcClient rpcClient, String poolId, String sorobanInitialPayload) throws Exception {
    String directory = WhirlpoolProtocol.getSorobanDirRegisterInput(whirlpoolNetwork, poolId);
    return rpcClient.directoryRemove(directory, sorobanInitialPayload);
  }

  public Collection<RegisterInputSoroban> getListRegisterInputSorobanByPoolId(
      RpcClientEncrypted rpcClient, String poolId) throws Exception {
    String directory = WhirlpoolProtocol.getSorobanDirRegisterInput(whirlpoolNetwork, poolId);
    Collection<SorobanMessageWithSender> sorobanMessageWithSenders =
        asyncUtil.blockingGet(rpcClient.listWithSender(directory));
    Collection<RegisterInputSoroban> results = new LinkedList<>();
    for (SorobanMessageWithSender sorobanMessageWithSender : sorobanMessageWithSenders) {
      RegisterInputSorobanMessage sorobanMessage =
          jsonUtil
              .getObjectMapper()
              .readValue(sorobanMessageWithSender.getPayload(), RegisterInputSorobanMessage.class);
      PaymentCode sorobanPaymentCode = new PaymentCode(sorobanMessageWithSender.getSender());
      RegisterInputSoroban registerInputSoroban =
          new RegisterInputSoroban(
              sorobanMessage, sorobanPaymentCode, sorobanMessageWithSender.getInitialPayload());
      results.add(registerInputSoroban);
    }

    // filter keep latest message for each utxo
    Map<String, RegisterInputSoroban> latestMessageByUtxo = new LinkedHashMap<>();
    for (RegisterInputSoroban ris : results) {
      RegisterInputSorobanMessage risb = ris.getSorobanMessage();
      String key = Utils.computeInputId(risb.utxoHash, risb.utxoIndex);
      RegisterInputSoroban existing = latestMessageByUtxo.get(key);
      if (existing == null || existing.getSorobanMessage().blockHeight < risb.blockHeight) {
        latestMessageByUtxo.put(key, ris);
      }
    }
    return latestMessageByUtxo.values();
  }
}
