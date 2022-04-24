package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.feeOpReturn.FeeOpReturnImplV1;
import com.samourai.whirlpool.protocol.feePayload.FeePayloadV1;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.beans.Tx0Validation;
import com.samourai.whirlpool.server.services.fee.WhirlpoolFeeData;
import org.bitcoinj.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class Tx0ValidationServiceV1Test extends Tx0ValidationServiceV0Test {

  @Override
  protected void setupFeeOpReturnImpl() {
    // do nothing = use FeeOpReturnV1
    Assertions.assertEquals(FeeOpReturnImplV1.OP_RETURN_VERSION, feePayloadService._getFeeOpReturnImplCurrent().getOpReturnVersion());
  }

  @Test
  public void validate_raw_noScodeV1() throws Exception {
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    String txHex =
            "01000000000101e99a82add547766622b2d239b5298b892ad59188d85fc3d43217b9ebbabbf7f70000000000ffffffff070000000000000000536a4c500ac54f393b102a8d39f5a19e3ee97c89952a37e6e14c25dd0cac7f0d6360f0c5e5f076b17fcc0ee6bcb8b4711ec302506bdbaf907eaeec6caaf2364b909ff954d4e9aeed9beb08e9333d28442254290198e00e0000000000160014c7723363f8df0bffe3dc45f54be7604687de8ab0a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014df3a4bc83635917ad18621f3ba78cef6469c5f5902473044022070f7ff5f81a4bf34ee29689d9704b657896e157920ed5900f1897ee6ff148e3102206516c8218bab4f0ed419669978b9f9f1674d3a3b17c47f8d7fd19e755cf4e3f4012102506bdbaf907eaeec6caaf2364b909ff954d4e9aeed9beb08e9333d284422542900000000";
    Transaction tx = txUtil.fromTxHex(params, txHex);

    Tx0Validation tx0Validation = tx0ValidationService.validate(tx, 1234, poolFee);
    Assertions.assertEquals(1, tx0Validation.getFeeOutput().getIndex());
    Assertions.assertNull(tx0Validation.getScodeConfig());

    int feeIndex = 1;
    short scodePayload = 0;
    WhirlpoolFeeData feeData = tx0Validation.getFeeData();
    Assertions.assertEquals(feeIndex, feeData.getFeeIndice());
    Assertions.assertEquals(scodePayload, feeData.getScodePayload());
    Assertions.assertEquals(FeePayloadV1.FEE_PAYLOAD_VERSION, feeData.getFeePayloadVersion());
    Assertions.assertEquals(FeeOpReturnImplV1.OP_RETURN_VERSION, feeData.getOpReturnVersion());

    // reverse parseAndValidate
    Integer[] strictModeVouts = new Integer[] {2, 3, 4, 5, 6};
    doParseAndValidate(tx0Validation, tx, serverPool, strictModeVouts);
  }

  @Test
  public void validate_raw_feePayloadInvalidV1() throws Exception {
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    String txHex =
            "01000000000101fe2c2024936ad04935cb528f90398efc63dce9e71e97a9f54cdc3a6d436782a90000000000ffffffff070000000000000000536a4c5017fb4e8e8e029e1e133068a809217bc262cda0f7ec875b5698ed83c5cd39a39ee2a5765e81189dc43727a858974b03c06794ff100ac14be2ad820d1ad33242c78d46782a5ab61053bdbd61aa3a65e20198e00e0000000000160014df3a4bc83635917ad18621f3ba78cef6469c5f59a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014a2fc114723a7924b0b056567b5c24d16ce8933690247304402201888fac50e12ff7dbe003d7b02f7173f8ef4b36a609b36007bd0101c26d037b502200ff4ed57cd4476bdcd2c047bfc698a58ff3ce5393e571005052c4b9dfcdf637b012103c06794ff100ac14be2ad820d1ad33242c78d46782a5ab61053bdbd61aa3a65e200000000";
    Transaction tx = txUtil.fromTxHex(params, txHex);
    Assertions.assertThrows(
            Exception.class, () -> tx0ValidationService.validate(tx, 1234, poolFee), "Not a valid TX0");
  }

  @Test
  public void validate_raw_noScode_invalidAddressV1() throws Exception {
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    String txHex =
            "010000000001011ad5af11db7bc7c6d3ee927a5af5a3bc4229c1709e540cac0b99da6b38f5d6d60000000000ffffffff070000000000000000536a4c5067e53ef61df33d8c34198100cf5a1af9003c043b7ea047506b3a373bb9711bb5aab30fdd6c770ae0390b1a04d3de03a65ca6b56e6f7267a60a9733ccc9c9ff5b78a1f8ffafd9e068d6ea1eb48c85b30198e00e00000000001600142a64f8ea17ebf6c5501bd0f96f7cf43114e26801a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014df3a4bc83635917ad18621f3ba78cef6469c5f5902483045022100864f9b5925bad709bb9b8b3df0e8fe23fd43824a3b8b30a08658c72bd435ec4e022071bd566897fbe0b4f360d5fe63a44302f6a501d9c55c27cd90f60e1e208d31f7012103a65ca6b56e6f7267a60a9733ccc9c9ff5b78a1f8ffafd9e068d6ea1eb48c85b300000000";
    Transaction tx = txUtil.fromTxHex(params, txHex);
    Assertions.assertThrows(
            Exception.class, () -> tx0ValidationService.validate(tx, 1234, poolFee), "Not a valid TX0");
  }

  @Test
  public void validate_raw_feePayloadValid_V1() throws Exception {
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    String txHex =
            "010000000001014392d8286cd2a0a668e8e7569ae4925aa46fa5c8acf59771d3c42378d02c39d40000000000ffffffff070000000000000000536a4c501b9a6c821a3e7bfddd5044427fe2f7330ce7d8eebf2d8a337e91209f86b8de40a4283005d11e3f2f3816552c272e03ff0f501cca3f10c9a00d38dfb9f3d89d66dd24577c2a94f4fe0ee02fccc17949015704000000000000160014df3a4bc83635917ad18621f3ba78cef6469c5f59a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a7143100cf8fa90500000000160014a2fc114723a7924b0b056567b5c24d16ce89336902483045022100c9b41eb3a16fa6833d93fa09e52c76b19c7b588f8d2c861bc04261ebbd7545a4022015401a50a41229fb0b93b8cb184fbee2021d291adfe72bcd6df012e2b5682a3b012103ff0f501cca3f10c9a00d38dfb9f3d89d66dd24577c2a94f4fe0ee02fccc1794900000000";
    Transaction tx = txUtil.fromTxHex(params, txHex);

    int feeIndex = 123456;
    short scodePayload = SCODE_FOO_PAYLOAD; // valid scodePayload
    short partnerPayload = 0;

    Tx0Validation tx0Validation = tx0ValidationService.validate(tx, 1234, poolFee);
    Assertions.assertNull(tx0Validation.getFeeOutput());
    Assertions.assertEquals(scodePayload, tx0Validation.getScodeConfig().getPayload());

    WhirlpoolFeeData feeData = tx0Validation.getFeeData();
    Assertions.assertEquals(feeIndex, feeData.getFeeIndice());
    Assertions.assertEquals(scodePayload, feeData.getScodePayload());
    Assertions.assertEquals(partnerPayload, feeData.getPartnerPayload());
    Assertions.assertEquals(FeePayloadV1.FEE_PAYLOAD_VERSION, feeData.getFeePayloadVersion());
    Assertions.assertEquals(FeeOpReturnImplV1.OP_RETURN_VERSION, feeData.getOpReturnVersion());

    // reverse parseAndValidate
    Integer[] strictModeVouts = new Integer[] {1, 2, 3, 4, 5, 6};
    doParseAndValidate(tx0Validation, tx, serverPool, strictModeVouts);
  }
}
