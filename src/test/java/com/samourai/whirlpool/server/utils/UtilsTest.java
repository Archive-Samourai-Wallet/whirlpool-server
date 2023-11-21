package com.samourai.whirlpool.server.utils;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.wallet.util.PrivKeyReader;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.SecretWalletContext;
import com.samourai.whirlpool.server.beans.TxOutSignature;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class UtilsTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void computeBlameIdentitifer_mustmix() {
    RegisteredInput confirmedInput =
        testUtils.computeConfirmedInput(
            "poolId", "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", 2, false);

    // mustmix => should ban TX0
    String expected = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    String actual = Utils.computeBlameIdentitifer(confirmedInput);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void computeBlameIdentitifer_liquidity() {
    RegisteredInput confirmedInput =
        testUtils.computeConfirmedInput(
            "poolId", "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", 2, true);

    // liquidity => should ban UTXO
    String expected = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187:2";
    String actual = Utils.computeBlameIdentitifer(confirmedInput);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void serializeOutput() throws Exception {
    Assertions.assertEquals(
        "04a6000000000000160014c63ba1b04bb4121280204fd420bdc541e1ed4f75",
        Hex.toHexString(
            Utils.serializeTransactionOutput(
                "bc1qcca6rvztksfp9qpqfl2zp0w9g8s76nm4de954y", 42500L, MainNetParams.get())));
  }

  @Test
  public void signTransactionOutput_1() throws Exception {
    NetworkParameters params = MainNetParams.get();
    ECKey ecKey =
        new PrivKeyReader("L3oTDdoxFDTZJcndCvCz5i7n34MjTG6iSSyYDPcXeoJvnDkMBavS", params).getKey();

    TxOutSignature tos =
        Utils.signTransactionOutput(
            "bc1q8yxgcltjgu6zekqspuhk7acdvht8pevwal23ye", 42500, params, ecKey);
    Assertions.assertEquals("1PWJ3QckGV921bZrrMwQXokhxXsUrKD3wt", tos.signingAddress);
    Assertions.assertEquals(
        "8e27a1795f45b0a8c87b5ea4a77cd0dfd57e58cdb724fced5db1cdf8f6f23ca3", tos.preHash);
    Assertions.assertEquals(
        "H1wBU1SJnkQ8K4QDn36TVQ9xYwKbF0zh2Ooqd+pKxqdrEiG7uWDGIFSNKyJanwlNFcB1XWrdhp+hyAYucTyPxM0=",
        tos.signature);
  }

  @Test
  public void signTransactionOutput_2() throws Exception {
    TxOutSignature tos =
        Utils.signTransactionOutput(
            "TB1QHH2PDL203J27ACMFDPHRLHE3UHL7F7W5NXVYTT",
            42500,
            params,
            serverContext.getSigningWallet());
    Assertions.assertEquals("mi42XN9J3eLdZae4tjQnJnVkCcNDRuAtz4", tos.signingAddress);
    Assertions.assertEquals(
        "41ac962525d867004e034ce22327c8ea5f5c57ea96e39502339c146fc306556c", tos.preHash);
    Assertions.assertEquals(
        "H+LHNMd4uOy5Nr/iMQqW+4IifA5v7WPQFnoxuoBQw0++aMFfeuYl1PFUXnKHqqotYg8oDvtcpA0ZhwGS+suGPAU=",
        tos.signature);
  }

  @Test
  public void signMessage() throws Exception {
    String message = "foo";
    SecretWalletContext signingWallet = serverContext.getSigningWallet();
    String signingAddress = signingWallet.getAddressString();
    Assertions.assertEquals("mi42XN9J3eLdZae4tjQnJnVkCcNDRuAtz4", signingAddress);

    String signature = Utils.signMessage(signingWallet, message);
    Assertions.assertEquals(
        "IOPYuUGGRACTiF8miDxEcVukQeORxK8wTo9tZc26R7hqSW11gwG8Zs32w4Q4pRtf3kV7bBfdItbXJaCA8mR9sEs=",
        signature);

    Assertions.assertTrue(
        messageSignUtil.verifySignedMessage(signingAddress, message, signature, params));
  }

  @Test
  public void computeSigningAddress() throws Exception {
    NetworkParameters params = TestNet3Params.get();
    SecretWalletContext signingWallet = serverContext.getSigningWallet();
    String signingAddress = signingWallet.getAddressString();
    Assertions.assertEquals("mi42XN9J3eLdZae4tjQnJnVkCcNDRuAtz4", signingAddress);
  }
}
