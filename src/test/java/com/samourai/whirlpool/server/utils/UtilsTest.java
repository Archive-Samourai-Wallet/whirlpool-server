package com.samourai.whirlpool.server.utils;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.wallet.bipFormat.BIP_FORMAT;
import com.samourai.wallet.util.PrivKeyReader;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
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
    ConfirmedInput confirmedInput =
        testUtils.computeConfirmedInput(
            "poolId", "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", 2, false);

    // mustmix => should ban TX0
    String expected = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    String actual = Utils.computeBlameIdentitifer(confirmedInput.getRegisteredInput());
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void computeBlameIdentitifer_liquidity() {
    ConfirmedInput confirmedInput =
        testUtils.computeConfirmedInput(
            "poolId", "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", 2, true);

    // liquidity => should ban UTXO
    String expected = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187:2";
    String actual = Utils.computeBlameIdentitifer(confirmedInput.getRegisteredInput());
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
  public void signTransactionOutput() throws Exception {
    NetworkParameters params = MainNetParams.get();
    ECKey ecKey =
        new PrivKeyReader("L3oTDdoxFDTZJcndCvCz5i7n34MjTG6iSSyYDPcXeoJvnDkMBavS", params).getKey();
    String address = BIP_FORMAT.LEGACY.getToAddress(ecKey, params);
    Assertions.assertEquals("1PWJ3QckGV921bZrrMwQXokhxXsUrKD3wt", address);

    Assertions.assertEquals(
        "H1wBU1SJnkQ8K4QDn36TVQ9xYwKbF0zh2Ooqd+pKxqdrEiG7uWDGIFSNKyJanwlNFcB1XWrdhp+hyAYucTyPxM0=",
        Utils.signTransactionOutput(
            "bc1q8yxgcltjgu6zekqspuhk7acdvht8pevwal23ye", 42500, params, ecKey));
  }
}
