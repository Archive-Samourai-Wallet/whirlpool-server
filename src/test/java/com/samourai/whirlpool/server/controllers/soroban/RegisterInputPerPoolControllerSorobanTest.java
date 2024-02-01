package com.samourai.whirlpool.server.controllers.soroban;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.MixDestination;
import com.samourai.whirlpool.client.mix.handler.UtxoWithBalance;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.tx0.ITx0PreviewServiceConfig;
import com.samourai.whirlpool.client.tx0.MockTx0PreviewService;
import com.samourai.whirlpool.client.tx0.MockTx0PreviewServiceConfig;
import com.samourai.whirlpool.client.tx0.Tx0PreviewService;
import com.samourai.whirlpool.client.wallet.data.coordinator.CoordinatorSupplier;
import com.samourai.whirlpool.client.wallet.data.coordinator.MockCoordinatorSupplier;
import com.samourai.whirlpool.client.wallet.data.minerFee.MockMinerFeeSupplier;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class RegisterInputPerPoolControllerSorobanTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired private RegisterInputPerPoolControllerSoroban registerInputPerPoolControllerSoroban;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true);
  }

  @Test
  public void registerInput() throws Exception {
    // mix config: wait for mustMix before confirming liquidities
    Pool pool = __nextMix(2, 0, 2, __getCurrentPoolId()).getPool();

    WhirlpoolClient whirlpoolClient = createClient();

    long inputBalance = pool.getDenomination();
    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    TxOutPoint txOutPoint = createAndMockTxOutPoint(inputAddress, inputBalance);
    UtxoWithBalance utxoWithBalance = txOutPoint.toUtxoWithBalance();

    // initial
    Assertions.assertEquals(0, pool.getLiquidityQueue().getSize());
    Assertions.assertEquals(false, pool.getLiquidityQueue().hasOutPoint(txOutPoint));

    MockMinerFeeSupplier minerFeeSupplier = new MockMinerFeeSupplier();
    ITx0PreviewServiceConfig tx0PreviewServiceConfig =
        new MockTx0PreviewServiceConfig(serverConfig.getWhirlpoolNetwork());
    Tx0PreviewService tx0PreviewService =
        new MockTx0PreviewService(minerFeeSupplier, tx0PreviewServiceConfig);
    CoordinatorSupplier coordinatorSupplier =
        new MockCoordinatorSupplier(tx0PreviewService, computeWhirlpoolWalletConfig());

    // register client
    Runnable doRegister =
        () -> {
          MixParams mixParams =
              whirlpoolClientService.computeMixParams(
                  pool, utxoWithBalance, ecKey, coordinatorSupplier);
          WhirlpoolClientListener listener =
              new WhirlpoolClientListener() {
                @Override
                public void success(Utxo receiveUtxo, MixDestination receiveDestination) {}

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
          registerInputPerPoolControllerSoroban._runOrchestrator();
        };
    doRegister.run();

    // check input registered
    Assertions.assertEquals(1, pool.getLiquidityQueue().getSize());
    Assertions.assertEquals(true, pool.getLiquidityQueue().hasOutPoint(txOutPoint));
    RegisteredInput registeredInput =
        pool.getLiquidityQueue()
            .findByOutPoint(utxoWithBalance.getHash(), utxoWithBalance.getIndex())
            .get();
    long lastSeen = registeredInput.getSorobanInput().getSorobanLastSeen();

    // register client again
    doRegister.run();

    // check sorobanLastSeen updated
    Assertions.assertEquals(1, pool.getLiquidityQueue().getSize());
    Assertions.assertEquals(true, pool.getLiquidityQueue().hasOutPoint(txOutPoint));
    registeredInput =
        pool.getLiquidityQueue()
            .findByOutPoint(utxoWithBalance.getHash(), utxoWithBalance.getIndex())
            .get();
    long newLastSeen = registeredInput.getSorobanInput().getSorobanLastSeen();
    Assertions.assertTrue(newLastSeen > lastSeen);
  }
}
