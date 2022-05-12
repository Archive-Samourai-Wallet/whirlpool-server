package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.protocol.feeOpReturn.FeeOpReturnImplV1;
import com.samourai.whirlpool.protocol.feePayload.FeePayloadV1;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.beans.Tx0Validation;
import com.samourai.whirlpool.server.services.fee.WhirlpoolFeeData;
import org.bitcoinj.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class Tx0ValidationServiceV1Test extends Tx0ValidationServiceV0Test {

  @Override
  protected void setupFeeOpReturnImpl() {
    // do nothing = use FeeOpReturnV1
    Assertions.assertEquals(
        FeeOpReturnImplV1.OP_RETURN_VERSION,
        feePayloadService._getFeeOpReturnImplCurrent().getOpReturnVersion());
  }

  @Test
  public void validate_raw_noScodeV1() throws Exception {
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    String txHex =
        "01000000000101fe0639f56380876b050dfdfaece9418f16761b9dd88807f8a4d1c0976aa9ddd60000000000ffffffff070000000000000000536a4c504b123f62de3e9099f101d62f5e423191c7fe3dc8fa79bb36f94a85d4cfe8db9e3e7eb6caa83b72394f2b9f777c9002b0cabed2accafb2240d0f20e8e54119db65e6d23f47aec86ae5cc123747865460198e00e0000000000160014c7723363f8df0bffe3dc45f54be7604687de8ab0a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014df3a4bc83635917ad18621f3ba78cef6469c5f59024730440220328b025a8f1b1804ac5b5f3f916bc1d52a69e377f34afeefac7a7ba9e467bc890220598260335a12c3a96df2387fb96aa7ac445897a1b3b5e00422f761f210b48e77012102b0cabed2accafb2240d0f20e8e54119db65e6d23f47aec86ae5cc1237478654600000000";
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
        "01000000000101d8d5150c4052d44ea3731b8d9095cf8a94b5041fb2068825587dd760aeab89960000000000ffffffff070000000000000000536a4c50ce7b8b2aa2717d26f2146c0b019f9bb3e3de062dda7a15054631862d2fe0fe4a7cc63e95b1dcb27a9ddc3c49cb5d03f4e381434d2ed455ff4fd98cb3cc186a76e9be2a125c0284a1c1485520d975ad0198e00e0000000000160014df3a4bc83635917ad18621f3ba78cef6469c5f59a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014a2fc114723a7924b0b056567b5c24d16ce89336902473044022024d03e75ec537d48b712a015e2caae2bfd2779c8b6983f06aaca9064dd033246022064219bba2bba60d9ba0e4fd7ff83441995455fa2dbd3c06171c4deb226a4229b012103f4e381434d2ed455ff4fd98cb3cc186a76e9be2a125c0284a1c1485520d975ad00000000";
    Transaction tx = txUtil.fromTxHex(params, txHex);
    Exception e =
        Assertions.assertThrows(
            Exception.class, () -> tx0ValidationService.validate(tx, 1234, poolFee));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());
  }

  @Test
  public void validate_raw_noScode_invalidAddressV1() throws Exception {
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    String txHex =
        "010000000001013d4d457b53f3946e8b3231860f12c5fcaba41cc4a0050ed3637174441de7289d0000000000ffffffff070000000000000000536a4c50fb376ed9f27a6b33c09692e3dad907316244bfa8b3c9eedf6ec1e74d76b60eb1e6b0b767cf734dcec7f5c2525586026dbcf9d98217dc07a89c2f14c0d48e0c70cc2ef6e6031a08f097505835216f460198e00e00000000001600142a64f8ea17ebf6c5501bd0f96f7cf43114e26801a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a71431008eb39a0500000000160014df3a4bc83635917ad18621f3ba78cef6469c5f590248304502210092b5e53195dd30dd3a030f1f65a8eb2c7d260b3ff25a6877d6a725bce9f8987b02203cdaaf21beb1d80250ea0a920f04d5567f698a9d104a32a2d9fd996192b138e40121026dbcf9d98217dc07a89c2f14c0d48e0c70cc2ef6e6031a08f097505835216f4600000000";
    Transaction tx = txUtil.fromTxHex(params, txHex);
    Exception e =
        Assertions.assertThrows(
            Exception.class, () -> tx0ValidationService.validate(tx, 1234, poolFee));
    Assertions.assertEquals("No valid fee payment found", e.getMessage());
  }

  @Test
  public void validate_raw_feePayloadValid_V1() throws Exception {
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    com.samourai.whirlpool.server.beans.Pool serverPool = poolService.getPool("0.01btc");
    serverPool._setPoolFee(poolFee);

    String txHex =
        "0100000000010160492b3b2ccd80cde911a68ab4d1c2ded928487c038397680e0c19bc1c010ea00000000000ffffffff070000000000000000536a4c50123828240f522de77288f136b6a319d8adedd314adf71bb321b7979c4cafe8c1c1e89dbdf55809ca6f13cf1984fd0342ee22cc4eed2af969f7266f1efd772d3648dafc27645ee4789c3a156e3625c4015704000000000000160014df3a4bc83635917ad18621f3ba78cef6469c5f59a6420f000000000016001429386be199b340466a45b488d8eef42f574d6eaaa6420f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070a6420f0000000000160014e0c3a6cc4f4eedfa72f6b6e9d6767e2a2eb8c09fa6420f0000000000160014eb241f46cc4cb5eb777d1b7ebaf28af3a7143100cf8fa90500000000160014a2fc114723a7924b0b056567b5c24d16ce893369024830450221008d8ccedcfe568a6e5233a3440888ca3852b96442962f2a40280943b512f014a50220748af96f7b94908db3be08ac9cd723a81f36a057b4b061b8b1170f9f49b0723e01210342ee22cc4eed2af969f7266f1efd772d3648dafc27645ee4789c3a156e3625c400000000";
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
