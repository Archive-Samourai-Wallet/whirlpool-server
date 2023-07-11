package com.samourai.whirlpool.server.services.soroban;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.soroban.client.RpcWallet;
import com.samourai.soroban.client.rpc.RpcSession;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.UtxoWithBalance;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.soroban.SorobanClientApi;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.protocol.rest.PoolInfoSoroban;
import com.samourai.whirlpool.protocol.soroban.RegisterCoordinatorSorobanMessage;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.orchestrators.SorobanCoordinatorOrchestrator;
import com.samourai.whirlpool.server.orchestrators.SorobanInputOrchestrator;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class SorobanCoordinatorServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private SorobanCoordinatorOrchestrator poolInfoOrchestrator;
  private SorobanInputOrchestrator registerInputOrchestrator;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true);
    poolInfoOrchestrator = sorobanCoordinatorService._getCoordinatorOrchestrator();
    registerInputOrchestrator = sorobanCoordinatorService._getInputOrchestrator();
  }

  @Test
  public void poolInfo() throws Exception {
    poolInfoOrchestrator._runOrchestrator();
    poolInfoOrchestrator._runOrchestrator();
    poolInfoOrchestrator._runOrchestrator();

    RpcSession rpcSession = rpcClientServiceServer.getRpcSession("test");
    SorobanClientApi sorobanClientApi = new SorobanClientApi(serverConfig.getWhirlpoolNetwork());

    // fetch pools from Soroban
    Collection<RegisterCoordinatorSorobanMessage> registerCoordinatorSorobanMessages =
        rpcSession.withRpcClient(
            rpcClient ->
                AsyncUtil.getInstance().blockingGet(sorobanClientApi.fetchCoordinators(rpcClient)));

    Assertions.assertEquals(1, registerCoordinatorSorobanMessages.size());
    Collection<PoolInfoSoroban> poolInfoSorobans =
        registerCoordinatorSorobanMessages.iterator().next().pools;
    Assertions.assertEquals(4, poolInfoSorobans.size());
  }

  @Test
  public void registerSorobanInput() throws Exception {
    // mix config: wait for mustMix before confirming liquidities
    Pool pool = __nextMix(2, 0, 2, __getCurrentPoolId()).getPool();

    WhirlpoolClient whirlpoolClient = createClient();
    RpcWallet rpcWallet = rpcWallet();

    long inputBalance = pool.getDenomination();
    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    TxOutPoint txOutPoint = createAndMockTxOutPoint(inputAddress, inputBalance);
    UtxoWithBalance utxoWithBalance = txOutPoint.toUtxoWithBalance();

    // initial
    Assertions.assertEquals(0, pool.getLiquidityQueue().getSize());
    Assertions.assertEquals(false, pool.getLiquidityQueue().hasInput(txOutPoint));

    // register client
    Runnable doRegister =
        () -> {
          MixParams mixParams =
              whirlpoolClientService.computeMixParams(rpcWallet, pool, utxoWithBalance, ecKey);
          WhirlpoolClientListener listener =
              new WhirlpoolClientListener() {
                @Override
                public void success(Utxo receiveUtxo) {}

                @Override
                public void fail(MixFailReason reason, String notifiableError) {}

                @Override
                public void progress(MixStep mixStep) {}
              };
          whirlpoolClient.whirlpool(mixParams, listener);
          synchronized (this) {
            try {
              wait(2000);
            } catch (InterruptedException e) {
            }
          }
          registerInputOrchestrator._runOrchestrator();
        };
    doRegister.run();

    // check input registered
    Assertions.assertEquals(1, pool.getLiquidityQueue().getSize());
    Assertions.assertEquals(true, pool.getLiquidityQueue().hasInput(txOutPoint));
    RegisteredInput registeredInput =
        pool.getLiquidityQueue()
            .findByUtxo(utxoWithBalance.getHash(), utxoWithBalance.getIndex())
            .get();
    long lastSeen = registeredInput.getSorobanLastSeen();

    // register client again
    doRegister.run();

    // check sorobanLastSeen updated
    Assertions.assertEquals(1, pool.getLiquidityQueue().getSize());
    Assertions.assertEquals(true, pool.getLiquidityQueue().hasInput(txOutPoint));
    registeredInput =
        pool.getLiquidityQueue()
            .findByUtxo(utxoWithBalance.getHash(), utxoWithBalance.getIndex())
            .get();
    long newLastSeen = registeredInput.getSorobanLastSeen();
    Assertions.assertTrue(newLastSeen > lastSeen);
  }
}
