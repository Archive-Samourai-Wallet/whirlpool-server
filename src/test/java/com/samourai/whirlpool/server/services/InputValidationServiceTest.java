package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class InputValidationServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final PoolFee POOL_FEE = new PoolFee(975000, null);

  @BeforeEach
  public void beforeEach() {
    dbService.__reset();
  }

  @Test
  public void checkInput_invalid() throws Exception {
    // txid not in db
    String txid = "7d14d7d85eeda1efe7593d89cc8b61940c4a17b9390ae471577bbdc489c542eb";

    // invalid
    Assertions.assertFalse(hasMixTxid(txid, 1000000));

    // reject when invalid
    Exception e =
        Assertions.assertThrows(IllegalInputException.class, () -> doCheckInput(txid, 0, POOL_FEE));
    Assertions.assertEquals("Input rejected (not a premix or whirlpool input)", e.getMessage());
  }

  @Test
  public void checkInput_invalidDenomination() throws Exception {
    // register as valid whirlpool txid
    String txid = "ae97a4d646cf96f01f16d845f1b2be7ff1eaa013b8c957caa8514bba28336f13";
    long denomination = 1000000;
    try {
      dbService.saveMixTxid(txid, denomination + 1);
    } catch (Exception e) {
    } // ignore duplicate

    // reject when invalid denomination
    Assertions.assertFalse(hasMixTxid(txid, denomination)); // invalid
    Exception e =
        Assertions.assertThrows(IllegalInputException.class, () -> doCheckInput(txid, 0, POOL_FEE));
    Assertions.assertEquals("Input rejected (not a premix or whirlpool input)", e.getMessage());
  }

  @Test
  public void checkInput_valid() throws Exception {
    // register as valid whirlpool txid
    String txid = "ae97a4d646cf96f01f16d845f1b2be7ff1eaa013b8c957caa8514bba28336f13";
    long denomination = 1000000;
    try {
      dbService.saveMixTxid(txid, denomination);
    } catch (Exception e) {
    } // ignore duplicate

    // accept when valid
    Assertions.assertTrue(hasMixTxid(txid, denomination)); // valid
    Assertions.assertTrue(doCheckInput(txid, 0, POOL_FEE)); // liquidity
  }

  private boolean hasMixTxid(String utxoHash, long denomination) {
    return dbService.hasMixTxid(utxoHash, denomination);
  }

  @Test
  public void checkInput_noFeePayload() throws Exception {
    // register as valid whirlpool txid
    String txid = "6588946af1d9d92b402fd672360fd12217abfaf6382ce644d358e8174781f0ce";
    long FEES_VALID_TX = 50000;

    // accept when valid mustMix, paid exact fee
    PoolFee poolFee = new PoolFee(FEES_VALID_TX, null);
    for (int i = 2; i < 8; i++) {
      Assertions.assertFalse(doCheckInput(txid, i, poolFee));
    }

    // reject when valid mustMix, paid more than fee
    poolFee = new PoolFee(FEES_VALID_TX - 1, null);
    for (int i = 2; i < 8; i++) {
      final int ii = i;
      final PoolFee myPoolFee = poolFee;
      Exception e =
          Assertions.assertThrows(
              IllegalInputException.class, () -> doCheckInput(txid, ii, myPoolFee));
      Assertions.assertEquals(
          "Input rejected (invalid fee for tx0=" + txid + ", x=0, scodePayload=no)",
          e.getMessage());
    }

    // reject when paid less than fee
    poolFee = new PoolFee(FEES_VALID_TX + 1, null);
    for (int i = 2; i < 8; i++) {
      final PoolFee myPoolFee = poolFee;
      final int ii = i;
      Exception e =
          Assertions.assertThrows(
              IllegalInputException.class, () -> doCheckInput(txid, ii, myPoolFee));
      Assertions.assertEquals(
          "Input rejected (invalid fee for tx0=" + txid + ", x=0, scodePayload=no)",
          e.getMessage());
    }
  }

  @Test
  public void checkInput_noFeePayload_invalidAddress() throws Exception {
    // register as valid whirlpool txid
    String txid = "7aa680b658cf26aa94944875d31dcd60db204e1e746dfd36cfcd677494ca89a4";
    long FEES_VALID_TX = 50000;

    // valid mustMix, paid exact fee
    final PoolFee poolFee = new PoolFee(FEES_VALID_TX, null);
    for (int i = 2; i < 8; i++) {
      final int ii = i;
      // invalid fee address
      Exception e =
          Assertions.assertThrows(
              IllegalInputException.class, () -> doCheckInput(txid, ii, poolFee));
      Assertions.assertEquals(
          "Input rejected (invalid fee for tx0=" + txid + ", x=643, scodePayload=no)",
          e.getMessage());
    }
  }

  @Test
  public void checkInput_feePayload_invalid() throws Exception {
    // reject nofee when unknown feePayload
    Exception e =
        Assertions.assertThrows(
            IllegalInputException.class,
            () ->
                doCheckInput(
                    "b3557587f87bcbd37e847a0fff0ded013b23026f153d85f28cb5d407d39ef2f3",
                    2,
                    POOL_FEE));
    Assertions.assertEquals(
        "Input rejected (invalid fee for tx0=b3557587f87bcbd37e847a0fff0ded013b23026f153d85f28cb5d407d39ef2f3, x=11, scodePayload=yes)",
        e.getMessage());
  }

  @Test
  public void checkInput_feePayload_valid() throws Exception {
    // accept when valid feePayload
    setScodeConfig("myscode", (short) 12345, 0, null);
    doCheckInput("b3557587f87bcbd37e847a0fff0ded013b23026f153d85f28cb5d407d39ef2f3", 2, POOL_FEE);
  }

  @Test
  public void checkInput_cascading_valid() throws Exception {
    /*
    Initial TX0 (0.01btc): 942225bfde0d07591ff815541a6a811e1dc378eb1facabf0f5a21ab96eb0be82
     in    0.02000346 BTC outpoint:20d1af237f19533a83b66cb882aa365808ae6560aeeeba49dd20938fd4dba09b:0
     in    0.00394064 BTC outpoint:6a753b58d8571fc10ca9a61dad6cd8b63791785a2811bf5747bac0093debebdf:2
     out  RETURN PUSHDATA1[6fff6f4adeeb081d02e88e15820499a2a22cbb749d1e43ba7b5347690429cac595ce04aa9f73c9364b7a5b076755036e350b47817fe80c931d2e7317d46b6017af2427f201bec425e41ae8d89a029d01] 0.00 BTC
     out  0[] PUSHDATA(20)[cfd4ef8b599a558d96bf3af1154c79bf1d158eab] 0.000425 BTC
     out  0[] PUSHDATA(20)[d05443f988227aea3cef0c5150fd2430a566c18a] 0.00350022 BTC
     out  0[] PUSHDATA(20)[1690c620153b1d8673ab15810b4a005b49688aa8] 0.01000262 BTC
     out  0[] PUSHDATA(20)[41eda3bd7b764345efbbb3057b87b2d9c7eff837] 0.01000262 BTC

    Cascading TX0 (0.001btc): d5c0995f415953a3ff809d298f6a861595763d3863e788442461f917a3c822ac
     in    0.00350022 BTC outpoint:942225bfde0d07591ff815541a6a811e1dc378eb1facabf0f5a21ab96eb0be82:2
     out  RETURN PUSHDATA1[ab41135fdfbbc1b2bca981c09f380aaec781facbb5b99029ca24470c4ab4fc0550411178154b37348a30641ba2c7036e350b47817fe80c931d2e7317d46b6017af2427f201bec425e41ae8d89a029d01] 0.00 BTC
     out  0[] PUSHDATA(20)[8b144ecee57f612af7dfc202b2f86b032b029cc4] 0.000025 BTC
     out  0[] PUSHDATA(20)[f827b33143d6a762ecd29790c8992d832eead9a8] 0.00045407 BTC
     out  0[] PUSHDATA(20)[372e2211f7c3621f1a2b348905445a0eb38c6b55] 0.00100262 BTC
     out  0[] PUSHDATA(20)[924a8e48b305d06d6d6c8508694dacbc556fb22a] 0.00100262 BTC
     out  0[] PUSHDATA(20)[f11b0fc3d9f8956e15ce5696fe7b92b4e1b6fc41] 0.00100262 BTC
    */

    // check initial TX0
    doCheckInput(
        "942225bfde0d07591ff815541a6a811e1dc378eb1facabf0f5a21ab96eb0be82",
        2,
        new PoolFee(42500, null));

    // check cascading TX0
    doCheckInput(
        "d5c0995f415953a3ff809d298f6a861595763d3863e788442461f917a3c822ac",
        2,
        new PoolFee(5000, null));
  }

  @Test
  public void checkInput_cascading_invalid_1() throws Exception {
    // this fake cascading TX0 has multiple inputs
    /*
    Fake cascading TX0 (0.01btc): e4ff15906752db752c2b66b3180a541617ffa4c37f6e2049fe7d71d043e4555d
     in    0.00092678 BTC outpoint:553e557ec7c613fb479c6f9360c3e7e02d3ec45cde4c7c41682b2750c3abd5e2:2
     in    0.01701512 BTC outpoint:98184340a6b719e5449fd0d5d404d02532e38f2d14028ae439e64627b53a0365:0
     in    0.00093477 BTC outpoint:abfd7ade3dd7492822e86219416ecd18c3125d5a885f6d6c50b724d73446f3b8:2
     in    0.00753736 BTC outpoint:ddacc53b359b252c7a20c645fad067859cddbfbb303981d24b123a06c46d6b5d:0
     out  RETURN PUSHDATA1[cd69c32e36fd4a866453c665dc599702148b9abab479b54fefb471455116e67b013124bba9952ff127816eae299e036e350b47817fe80c931d2e7317d46b6017af2427f201bec425e41ae8d89a029d01] 0.00 BTC
     out  0[] PUSHDATA(20)[b7e15fa0251cb0b183b1924d8360e9e8ed3aac5d] 0.000045 BTC
     out  0[] PUSHDATA(20)[262db5d0e67310eed49779d3e2dfadfa0685ac9a] 0.00100262 BTC
     out  0[] PUSHDATA(20)[3d48125b6e51bdf8803d10d10250c34f5867dd51] 0.00100262 BTC
     out  0[] PUSHDATA(20)[5c336ba5c774f31da50f5659cc623dd7bd65e54d] 0.00100262 BTC
     out  0[] PUSHDATA(20)[6ed65a10b885fa58d6fefe8d36a61f93f88a050d] 0.00100262 BTC
     out  0[] PUSHDATA(20)[73dac72c581809f20123fbbcf0caae3cb60f6c94] 0.00100262 BTC
     out  0[] PUSHDATA(20)[7a05b67f6ca96d0a38050b8b88c5f19606923387] 0.00100262 BTC
     out  0[] PUSHDATA(20)[833e54dd2bdc90a6d92aedbecef1ca9cdb24a4c4] 0.00100262 BTC
     out  0[] PUSHDATA(20)[adb93750e1ffcfcefc54c6be67bd3011878a5aa5] 0.00100262 BTC
     out  0[] PUSHDATA(20)[b21c13325c88e5357ad841885533c69628e8eae1] 0.00100262 BTC
     out  0[] PUSHDATA(20)[b4c878dca1c29556ba62439c3ea63da265fdad1c] 0.00100262 BTC
     out  0[] PUSHDATA(20)[c71ee11f2e177408ac7b311171355b954364581c] 0.01632509 BTC
    */

    // check initial TX0
    try {
      doCheckInput(
          "e4ff15906752db752c2b66b3180a541617ffa4c37f6e2049fe7d71d043e4555d",
          2,
          new PoolFee(5000, null));
      Assertions.assertTrue(false);
    } catch (IllegalInputException e) {
      Assertions.assertEquals(
          "Input rejected (invalid cascading for tx0=e4ff15906752db752c2b66b3180a541617ffa4c37f6e2049fe7d71d043e4555d)",
          e.getMessage());
    }
  }

  @Test
  public void checkInput_cascading_invalid_2() throws Exception {
    // this fake cascading TX0 uses CASCADING scode but input#0 is not a parent TX0.
    /*
    fa39e6d6d27d4d2fb40c0771b9937528acc6f7b80894e2ba42842fe7417d5e2c
     in    0.02048008 BTC
          outpoint:60b3582abca00e3f474abd8b991a2219e61c98e1716e3c487bc70e91854365fb:0
     out  RETURN PUSHDATA1[47548ea07887afad654f7c9eab436b0b6b22738ca5c876aacbb39c33dec0c610f7413492274763079d83d8070b36036e350b47817fe80c931d2e7317d46b6017af2427f201bec425e41ae8d89a029d01] 0.00 BTC
     out  0[] PUSHDATA(20)[dc6ae719cd9f60c5dcd6cccbc21de06c44096c44] 0.0000425 BTC
     out  0[] PUSHDATA(20)[1e19ce4c45b4b25d65b44aaead6a59d7fcca2c1f] 0.00100262 BTC
     out  0[] PUSHDATA(20)[238e0ea04a88cae5cefd06220cc069915e2b4062] 0.00100262 BTC
     out  0[] PUSHDATA(20)[585e84212f4e8ab85febcb04c42b25f9b2e5abd8] 0.00100262 BTC
     out  0[] PUSHDATA(20)[9ebdb1244c3d7b071ac7356a1f7a47a0b7096ad0] 0.00100262 BTC
     out  0[] PUSHDATA(20)[9ecfbbe9c2c7b60ee8dd2db4a6b910a8f57f1501] 0.00100262 BTC
     out  0[] PUSHDATA(20)[a42db9573f9cc15f5d2b4fc04c392d9d73b93c4a] 0.00100262 BTC
     out  0[] PUSHDATA(20)[b39a130d600fb6b01108c8eeb45bc5ab59add6e2] 0.00100262 BTC
     out  0[] PUSHDATA(20)[ead26d39d8a04d41956a552041aae9898c38b690] 0.00100262 BTC
     out  0[] PUSHDATA(20)[ec795d36ce08ce8ae3d2244bb989e0781b989528] 0.00100262 BTC
     out  0[] PUSHDATA(20)[fceca503378f76facd722a45e4fdb6799a24f372] 0.00100262 BTC
     out  0[] PUSHDATA(20)[3893107637aeaff77bbcbf8e6d2dadf1af6eb2c0] 0.01039571 BTC
    */

    // check initial TX0
    try {
      doCheckInput(
          "fa39e6d6d27d4d2fb40c0771b9937528acc6f7b80894e2ba42842fe7417d5e2c",
          2,
          new PoolFee(5000, null));
      Assertions.assertTrue(false);
    } catch (IllegalInputException e) {
      Assertions.assertEquals(
          "Input rejected (invalid cascading for tx0=fa39e6d6d27d4d2fb40c0771b9937528acc6f7b80894e2ba42842fe7417d5e2c)",
          e.getMessage());
    }
  }

  private boolean doCheckInput(String utxoHash, long utxoIndex, PoolFee poolFee)
      throws NotifiableException {
    RpcTransaction rpcTx =
        blockchainDataService
            .getRpcTransaction(utxoHash)
            .orElseThrow(() -> new NoSuchElementException(utxoHash + "-" + utxoIndex));
    long inputValue = rpcTx.getTx().getOutput(utxoIndex).getValue().getValue();
    boolean hasMixTxid = hasMixTxid(utxoHash, inputValue);
    boolean isLiquidity =
        inputValidationService.checkInputProvenance(
            rpcTx.getTx(), rpcTx.getTxTime(), poolFee, hasMixTxid);
    return isLiquidity;
  }
}
