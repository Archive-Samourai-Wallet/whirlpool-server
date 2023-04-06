package com.samourai.whirlpool.server.services.soroban;

import com.samourai.soroban.client.meeting.SorobanMessageWithSender;
import com.samourai.soroban.client.rpc.RpcClientEncrypted;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.wallet.util.JSONUtils;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.PoolInfoSoroban;
import com.samourai.whirlpool.protocol.soroban.InviteMixSorobanMessage;
import com.samourai.whirlpool.protocol.soroban.PoolInfoSorobanMessage;
import com.samourai.whirlpool.protocol.soroban.RegisterInputSorobanMessage;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisterInputSoroban;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import io.reactivex.Completable;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SorobanCoordinatorApi {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final AsyncUtil asyncUtil = AsyncUtil.getInstance();
  private static final JSONUtils jsonUtil = JSONUtils.getInstance();

  public SorobanCoordinatorApi() {}

  public Completable registerPools(
      RpcClientEncrypted rpcClient, Collection<PoolInfoSoroban> poolInfos) throws Exception {
    String directory = WhirlpoolProtocol.getSorobanDirPools();
    PoolInfoSorobanMessage message = new PoolInfoSorobanMessage(poolInfos);
    return rpcClient.send(directory, message.toPayload());
  }

  public Completable inviteToMix(
      RpcClientEncrypted rpcClient,
      RegisteredInput registeredInput,
      Mix mix,
      String coordinatorIp,
      PaymentCode paymentCodeCoordinator)
      throws Exception {
    InviteMixSorobanMessage inviteMixSorobanMessage =
        new InviteMixSorobanMessage(mix.getMixId(), mix.getPublicKey(), coordinatorIp);

    PaymentCode paymentCodeClient = registeredInput.getSorobanPaymentCode();
    String directory =
        WhirlpoolProtocol.getSorobanDirSharedNotify(paymentCodeCoordinator, paymentCodeClient);
    return rpcClient.sendEncrypted(
        directory, inviteMixSorobanMessage.toPayload(), paymentCodeClient);
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
          new RegisterInputSoroban(sorobanMessage, sorobanPaymentCode);
      results.add(registerInputSoroban);
    }
    return results;
  }
}
