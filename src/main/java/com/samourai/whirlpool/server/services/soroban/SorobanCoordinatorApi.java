package com.samourai.whirlpool.server.services.soroban;

import com.samourai.soroban.client.RpcWallet;
import com.samourai.soroban.client.rpc.RpcClientEncrypted;
import com.samourai.soroban.client.rpc.RpcMode;
import com.samourai.wallet.bip47.BIP47UtilGeneric;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.wallet.util.JSONUtils;
import com.samourai.wallet.util.Util;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolNetwork;
import com.samourai.whirlpool.protocol.WhirlpoolProtocolSoroban;
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
import io.reactivex.Completable;
import io.reactivex.Single;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.stream.Collectors;
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
  private WhirlpoolProtocolSoroban whirlpoolProtocolSoroban;

  public SorobanCoordinatorApi(
      WhirlpoolServerConfig serverConfig, WhirlpoolProtocolSoroban whirlpoolProtocolSoroban) {
    this.whirlpoolNetwork = serverConfig.getWhirlpoolNetwork();
    this.whirlpoolProtocolSoroban = whirlpoolProtocolSoroban;
  }

  public Completable registerCoordinator(
      RpcClientEncrypted rpcClient,
      String coordinatorId,
      String urlClear,
      String urlOnion,
      Collection<PoolInfoSoroban> poolInfos)
      throws Exception {
    String directory = whirlpoolProtocolSoroban.getDirCoordinators(whirlpoolNetwork);
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
        whirlpoolProtocolSoroban.getDirRegisterInputResponse(
            rpcWalletCoordinator, paymentCodeClient, bip47Util, params);
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
        whirlpoolProtocolSoroban.getDirRegisterInputResponse(
            rpcWalletCoordinator, paymentCodeClient, bip47Util, params);
    return rpcClient.sendEncrypted(
        directory, inviteMixSorobanMessage.toPayload(), paymentCodeClient, RpcMode.SHORT);
  }

  public Collection<RegisterInputSoroban> getListRegisterInputSorobanByPoolId(
      RpcClientEncrypted rpcClient, String poolId) throws Exception {
    String directory = whirlpoolProtocolSoroban.getDirRegisterInput(whirlpoolNetwork, poolId);
    // list soroban inputs
    return asyncUtil.blockingGet(rpcClient.listWithSender(directory)).stream()
        // keep only one payload per sender
        .filter(Util.distinctBy(p -> p.getSender()))
        // parse
        .map(
            sorobanMessageWithSender -> {
              try {
                RegisterInputSorobanMessage sorobanMessage =
                    jsonUtil
                        .getObjectMapper()
                        .readValue(
                            sorobanMessageWithSender.getPayload(),
                            RegisterInputSorobanMessage.class);
                PaymentCode sorobanPaymentCode =
                    new PaymentCode(sorobanMessageWithSender.getSender());
                return new RegisterInputSoroban(
                    sorobanMessage,
                    sorobanPaymentCode,
                    sorobanMessageWithSender.getInitialPayload());
              } catch (Exception e) {
                return null;
              }
            })
        .filter(o -> o != null)
        .collect(Collectors.toList());
  }

  public WhirlpoolProtocolSoroban getWhirlpoolProtocolSoroban() {
    return whirlpoolProtocolSoroban;
  }
}
