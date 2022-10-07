package com.samourai.whirlpool.server.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.VarInt;
import org.bouncycastle.util.encoders.Base64;

public class ECKeyUtils {
  // adapted from bitcoinj.Utils.formatMessageForSigning(String) to format byte[]
  private static byte[] formatMessageForSigning(byte[] messageBytes) {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      bos.write(org.bitcoinj.core.Utils.BITCOIN_SIGNED_MESSAGE_HEADER_BYTES.length);
      bos.write(org.bitcoinj.core.Utils.BITCOIN_SIGNED_MESSAGE_HEADER_BYTES);
      VarInt size = new VarInt((long) messageBytes.length);
      bos.write(size.encode());
      bos.write(messageBytes);
      return bos.toByteArray();
    } catch (IOException var4) {
      throw new RuntimeException(var4);
    }
  }

  // adapted from ECKey.signMessage(String) to sign byte[] for Straylight
  public static String signMessage(ECKey ecKey, byte[] message) throws Exception {
    Sha256Hash hash = Sha256Hash.twiceOf(formatMessageForSigning(message));
    ECKey.ECDSASignature sig = ecKey.sign(hash);

    int recId = -1;
    int headerByte;
    for (headerByte = 0; headerByte < 4; ++headerByte) {
      ECKey k = ecKey.recoverFromSignature(headerByte, sig, hash, ecKey.isCompressed());
      if (k != null && k.getPublicKeyAsHex().equals(ecKey.getPublicKeyAsHex())) {
        recId = headerByte;
        break;
      }
    }

    headerByte = recId + 27 + (ecKey.isCompressed() ? 4 : 0);
    byte[] sigData = new byte[65];
    sigData[0] = (byte) headerByte;
    System.arraycopy(org.bitcoinj.core.Utils.bigIntegerToBytes(sig.r, 32), 0, sigData, 1, 32);
    System.arraycopy(org.bitcoinj.core.Utils.bigIntegerToBytes(sig.s, 32), 0, sigData, 33, 32);
    return new String(Base64.encode(sigData), Charset.forName("UTF-8"));
  }
}
