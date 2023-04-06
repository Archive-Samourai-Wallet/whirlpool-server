package com.samourai.whirlpool.server.orchestrators;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.soroban.client.RpcWallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.UtxoWithBalance;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class SorobanRegisterinputOrchestratorTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true);
  }

  @Test
  public void registerSorobanInput() throws Exception {
    Pool pool = poolService.getPools().iterator().next();
    WhirlpoolClient whirlpoolClient = createClient();
    RpcWallet rpcWallet = rpcWallet();

    long inputBalance = pool.getDenomination();
    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    TxOutPoint txOutPoint = createAndMockTxOutPoint(inputAddress, inputBalance);
    UtxoWithBalance utxoWithBalance = txOutPoint.toUtxoWithBalance();
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
      wait(30000);
    }

    Assertions.assertEquals(1, pool.getLiquidityQueue().getSize());
    Assertions.assertEquals(1, pool.getLiquidityQueue().hasInput(txOutPoint));
  }
}
