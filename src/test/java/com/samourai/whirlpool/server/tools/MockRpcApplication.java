package com.samourai.whirlpool.server.tools;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.whirlpool.server.integration.AbstractJsonRpcClientTest;
import com.samourai.whirlpool.server.services.rpc.RpcRawTransactionResponse;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/** Utility for RPC testing. */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class MockRpcApplication extends AbstractJsonRpcClientTest {
  private static final Logger log = LoggerFactory.getLogger(MockRpcApplication.class);

  @Test
  public void testRun() throws Exception {

    log.info("------------ application-rpc ------------");

    String[] txids = {
      "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187",
      "7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16",
      "5369dfb71b36ed2b91ca43f388b869e617558165e4f8306b80857d88bdd624f2",
      "3bb546df988d8a577c2b2f216a18b7e337ebaf759187ae88e0eee01829f04eb1",
      "96cebec97115f59339a9053b6084aab5869adeefdbdbe974b74bfdbf3b8eaac3",
      // Tx0ServiceTest
      "781bc448f381e160d014f5515daf8a175bb689112c7755c70c6bbdbb32af56cc",
      "c1da465e30f3a7d19d3f3d254f387782247ea48cf22b832c474c37d17394cae6",
      "1d8a370ded61c81c925151ba7112d61d1535f7035756c43d2d5a77903afddd40",
      "8d0bf279b4a7fe4ad27c49a7546cab1c10201ee3a1105e502afb5c31908f4771"
    };
    for (String txid : txids) {
      mockRpcTx(txid);
    }
  }

  private void mockRpcTx(String txid) throws Exception {
    try {
      Optional<RpcRawTransactionResponse> rpcTxResponse = rpcClientService.getRawTransaction(txid);
      Assert.assertTrue(rpcTxResponse.isPresent());
      testUtils.writeMockRpc(txid, rpcTxResponse.get().getHex());
    } catch (Exception e) {
      log.error("", e);
    }
  }
}
