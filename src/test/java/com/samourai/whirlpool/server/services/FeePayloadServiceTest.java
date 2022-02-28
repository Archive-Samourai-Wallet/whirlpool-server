package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.services.fee.WhirlpoolFeeData;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class FeePayloadServiceTest extends AbstractIntegrationTest {

  @Test
  public void encodeFeePayload() throws Exception {
    // feeVersion:short(2) | indice:int(4) | feePayload:short(2) | feePartner:short(2)
    Assertions.assertEquals(
        WhirlpoolProtocol.FEE_PAYLOAD_LENGTH,
        feePayloadService.encodeFeePayload(123456, (short) 1234, (short) 0).length);

    // without feePayload
    Assertions.assertEquals(
        "00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000",
        bytesToBinaryString(feePayloadService.encodeFeePayload(0, (short) 0, (short) 0)));
    assertEqualsFeeData(
        0,
        (short) 0,
        (short) 0,
        feePayloadService.decodeFeePayload(
            bytesFromBinaryString(
                "00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000"),
            null));

    Assertions.assertEquals(
        "00000000 00000001 00000000 00000000 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000",
        bytesToBinaryString(feePayloadService.encodeFeePayload(1, (short) 0, (short) 0)));
    assertEqualsFeeData(
        1,
        (short) 0,
        (short) 0,
        feePayloadService.decodeFeePayload(
            bytesFromBinaryString(
                "00000000 00000001 00000000 00000000 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000"),
            null));

    Assertions.assertEquals(
        "00000000 00000001 00000000 00000001 11100010 01000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000",
        bytesToBinaryString(feePayloadService.encodeFeePayload(123456, (short) 0, (short) 0)));
    assertEqualsFeeData(
        123456,
        (short) 0,
        (short) 0,
        feePayloadService.decodeFeePayload(
            bytesFromBinaryString(
                "00000000 00000001 00000000 00000001 11100010 01000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000"),
            null));

    // with feePayload
    Assertions.assertEquals(
        "00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000",
        bytesToBinaryString(feePayloadService.encodeFeePayload(0, (short) 1, (short) 0)));
    assertEqualsFeeData(
        0,
        (short) 1,
        (short) 0,
        feePayloadService.decodeFeePayload(
            bytesFromBinaryString(
                "00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000"),
            null));

    Assertions.assertEquals(
        "00000000 00000001 00000000 00000000 00000000 00000001 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000",
        bytesToBinaryString(feePayloadService.encodeFeePayload(1, (short) 1, (short) 0)));
    assertEqualsFeeData(
        1,
        (short) 1,
        (short) 0,
        feePayloadService.decodeFeePayload(
            bytesFromBinaryString(
                "00000000 00000001 00000000 00000000 00000000 00000001 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000"),
            null));

    Assertions.assertEquals(
        "00000000 00000001 00000000 00000001 11100010 01000000 00000001 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000",
        bytesToBinaryString(feePayloadService.encodeFeePayload(123456, (short) 257, (short) 0)));
    assertEqualsFeeData(
        123456,
        (short) 257,
        (short) 0,
        feePayloadService.decodeFeePayload(
            bytesFromBinaryString(
                "00000000 00000001 00000000 00000001 11100010 01000000 00000001 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000"),
            null));

    // with feePartner
    Assertions.assertEquals(
        "00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000",
        bytesToBinaryString(feePayloadService.encodeFeePayload(0, (short) 0, (short) 1)));
    assertEqualsFeeData(
        0,
        (short) 0,
        (short) 1,
        feePayloadService.decodeFeePayload(
            bytesFromBinaryString(
                "00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000"),
            null));

    Assertions.assertEquals(
        "00000000 00000001 00000000 00000000 00000000 00000001 00000000 00000001 00000011 00111010 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000",
        bytesToBinaryString(feePayloadService.encodeFeePayload(1, (short) 1, (short) 826)));
    assertEqualsFeeData(
        1,
        (short) 1,
        (short) 826,
        feePayloadService.decodeFeePayload(
            bytesFromBinaryString(
                "00000000 00000001 00000000 00000000 00000000 00000001 00000000 00000001 00000011 00111010 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000"),
            null));

    Assertions.assertEquals(
        "00000000 00000001 00000000 00000001 11100010 01000000 00000001 00000001 00110000 00111001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000",
        bytesToBinaryString(
            feePayloadService.encodeFeePayload(123456, (short) 257, (short) 12345)));
    assertEqualsFeeData(
        123456,
        (short) 257,
        (short) 12345,
        feePayloadService.decodeFeePayload(
            bytesFromBinaryString(
                "00000000 00000001 00000000 00000001 11100010 01000000 00000001 00000001 00110000 00111001 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000"),
            null));
  }

  @Test
  public void decodeEncodedBytes() throws Exception {
    for (int i = 0; i < 1000; i++) {
      byte[] encoded = feePayloadService.encodeFeePayload(i, (short) 12345, (short) 1234);
      assertEqualsFeeData(
          i, (short) 12345, (short) 1234, feePayloadService.decodeFeePayload(encoded, null));
    }
  }

  private static String bytesToBinaryString(byte[] bytes) {
    List<String> strs = new ArrayList<>();
    for (byte b : bytes) {
      String str = String.format("%8s", Integer.toBinaryString((b + 256) % 256)).replace(' ', '0');
      strs.add(str);
    }
    return StringUtils.join(strs.toArray(), " ");
  }

  private static byte[] bytesFromBinaryString(String str) {
    String[] bytesStrs = str.split(" ");
    byte[] result = new byte[bytesStrs.length];
    for (int i = 0; i < bytesStrs.length; i++) {
      String byteStr = bytesStrs[i];
      result[i] = (byte) (int) (Integer.valueOf(byteStr, 2));
    }
    return result;
  }

  private void assertEqualsFeeData(
      int feeIndice, short scodePayload, short partnerPayload, WhirlpoolFeeData feeData) {
    Assertions.assertEquals(feeIndice, feeData.getFeeIndice());
    Assertions.assertEquals(scodePayload, feeData.getScodePayload());
    Assertions.assertEquals(partnerPayload, feeData.getPartnerPayload());
  }
}
