package com.samourai.whirlpool.server.services.soroban;

import com.samourai.soroban.client.RpcWallet;
import com.samourai.soroban.client.meeting.SorobanMessageWithSender;
import com.samourai.soroban.client.rpc.RpcClient;
import com.samourai.soroban.client.rpc.RpcClientEncrypted;
import com.samourai.wallet.bip47.BIP47UtilGeneric;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.wallet.util.JSONUtils;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.CoordinatorInfo;
import com.samourai.whirlpool.protocol.rest.PoolInfoSoroban;
import com.samourai.whirlpool.protocol.soroban.InviteMixSorobanMessage;
import com.samourai.whirlpool.protocol.soroban.RegisterCoordinatorSorobanMessage;
import com.samourai.whirlpool.protocol.soroban.RegisterInputSorobanMessage;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisterInputSoroban;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import io.reactivex.Completable;
import io.reactivex.Single;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
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

  public SorobanCoordinatorApi() {}

  public Completable registerCoordinator(
      RpcClientEncrypted rpcClient,
      String coordinatorId,
      String urlClear,
      String urlOnion,
      Collection<PoolInfoSoroban> poolInfos)
      throws Exception {
    String directory = WhirlpoolProtocol.getSorobanDirCoordinators();
    CoordinatorInfo coordinatorInfo = new CoordinatorInfo(coordinatorId, urlClear, urlOnion);
    RegisterCoordinatorSorobanMessage message =
        new RegisterCoordinatorSorobanMessage(coordinatorInfo, poolInfos);
    return rpcClient.send(directory, message.toPayload());
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
        WhirlpoolProtocol.getSorobanDirInviteMixSend(
            rpcWalletCoordinator,
            paymentCodeClient,
            registeredInput.getOutPoint().getHash(),
            registeredInput.getOutPoint().getIndex(),
            bip47Util,
            params);
    return rpcClient.sendEncrypted(
        directory, inviteMixSorobanMessage.toPayload(), paymentCodeClient);
  }

  public Single<Map<String, Object>> unregisterInput(
      RpcClient rpcClient, RegisteredInput registeredInput) throws Exception {
    String directory = WhirlpoolProtocol.getSorobanDirRegisterInput(registeredInput.getPoolId());
    return rpcClient.directoryRemove(directory, registeredInput.getSorobanInitialPayload());
  }

  public Collection<RegisterInputSoroban> getListRegisterInputSorobanByPoolId(
      RpcClientEncrypted rpcClient, String poolId) throws Exception {
    String directory = WhirlpoolProtocol.getSorobanDirRegisterInput(poolId);
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
    return results;
  }
}
