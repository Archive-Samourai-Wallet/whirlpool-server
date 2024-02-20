package com.samourai.whirlpool.server.controllers.soroban;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0DataRequest;
import com.samourai.whirlpool.protocol.soroban.payload.tx0.Tx0PushRequest;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class Tx0PerPoolControllerSorobanTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Tx0PerPoolControllerSoroban tx0Controller;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    Pool pool = poolService.getPool("0.01btc");
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    pool._setPoolFee(poolFee);

    tx0Controller =
        new Tx0PerPoolControllerSoroban(
            serverContext, tx0Service, sorobanAppWhirlpool, pool.getPoolId());
  }

  @Test
  public void tx0Data_noScode() throws Exception {
    boolean cascading = false;
    Tx0DataRequest tx0DataRequest = new Tx0DataRequest(null, null, cascading);
    tx0Controller.tx0Data(tx0DataRequest);
  }

  @Test
  public void tx0Data_cascading_noFee() throws Exception {
    boolean cascading = true;
    Tx0DataRequest request = new Tx0DataRequest(null, null, cascading);
    tx0Controller.tx0Data(request);
  }

  @Test
  public void pushTx0_feePayloadValid_0() throws Exception {
    String txHex =
        "0100000000010159f41e245a379baa06661e513508e65b31b7a6b2815258d2c772775068f739cf0000000000ffffffff070000000000000000426a4092c02c0b671d2f725bfe50fe6c777ed1461e535f06865b833daeb861e9166e1e26bcc1a86eaa2f5fbe064cad8e2ece5f3ea1c7721d6134e13a3a3b9a57cb97f95704000000000000160014df3a4bc83635917ad18621f3ba78cef6469c5f59a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a7143100cf8fa90500000000160014a2fc114723a7924b0b056567b5c24d16ce89336902483045022100e7694e7ee44d404da7f0ff76a45d8f60b9ca6a13a013b61b0f22b4d735e9a79402202647897ff6e6fa8f38ac808ef3de19788eaf27e57dd2f145ec3f0c22a0447ed5012102f231dd2f7eff90fe1a770ba585b52d33f6814f883398e4427de3f73203b5d8e300000000";
    Tx0PushRequest request = new Tx0PushRequest(tx64FromTxHex(txHex), "0.01btc");

    tx0Controller.tx0Push(request);
  }

  @Test
  public void pushTx0_feePayloadValid_noScode() throws Exception {
    String txHex =
        "010000000001011c0c981a33079a5f0e71445de7c0b5f776afc03cfdcdc9594465f7c047cd8b6f0000000000ffffffff070000000000000000426a40345bc3d1bd80d66f0f9b2683bba9b2aab388d175ec707d1689d530fcc138ad783882fa661e85607647067e0b112f6690a4505db2947339f100c00045694e43d798e00e0000000000160014c7723363f8df0bffe3dc45f54be7604687de8ab0a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014df3a4bc83635917ad18621f3ba78cef6469c5f590248304502210091724bed32f81fd45fc76166c203d2a7b31cce45117c0b6041c65aa39adc22be022007776fb85d237d1468401ccf50f8c1171289e4960b4a512920f206dcb0b37cfb012103858577e052ab063489c145bd785089dd01f46df220cc858e8b058d140f185ad000000000";
    Tx0PushRequest request = new Tx0PushRequest(tx64FromTxHex(txHex), "0.01btc");

    tx0Controller.tx0Push(request);
  }
}
