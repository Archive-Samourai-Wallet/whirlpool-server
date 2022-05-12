package com.samourai.whirlpool.server.services;

import com.samourai.wallet.bip47.rpc.BIP47Account;
import com.samourai.whirlpool.protocol.feeOpReturn.FeeOpReturn;
import com.samourai.whirlpool.protocol.feeOpReturn.FeeOpReturnImpl;
import com.samourai.whirlpool.protocol.feeOpReturn.FeeOpReturnImplV0;
import com.samourai.whirlpool.protocol.feeOpReturn.FeeOpReturnImplV1;
import com.samourai.whirlpool.protocol.feePayload.FeePayloadV1;
import com.samourai.whirlpool.server.services.fee.WhirlpoolFeeData;
import com.samourai.whirlpool.server.services.fee.WhirlpoolFeeOutput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeePayloadService {
  private static final Logger log = LoggerFactory.getLogger(FeePayloadService.class);

  public static final short SCODE_PAYLOAD_NONE = 0;

  private FeeOpReturnImpl feeOpReturnImplCurrent;
  private FeeOpReturnImpl[] feeOpReturnImpls;

  public FeePayloadService(
      FeeOpReturnImplV0 feeOpReturnImplV0, FeeOpReturnImplV1 feeOpReturnImplV1) {
    this.feeOpReturnImpls = new FeeOpReturnImpl[] {feeOpReturnImplV0, feeOpReturnImplV1};
    this.feeOpReturnImplCurrent = feeOpReturnImplV1; // use FeeOpReturnImplV1
  }

  // for tests only
  protected void _setFeeOpReturnImplCurrent(FeeOpReturnImpl FeeOpReturnImplCurrent) {
    this.feeOpReturnImplCurrent = FeeOpReturnImplCurrent;
  }

  // for tests only
  protected FeeOpReturnImpl _getFeeOpReturnImplCurrent() {
    return feeOpReturnImplCurrent;
  }

  public boolean acceptsOpReturn(byte[] opReturn) {
    for (FeeOpReturnImpl feeOpReturnImpl : feeOpReturnImpls) {
      if (feeOpReturnImpl.acceptsOpReturn(opReturn)) {
        return true;
      }
    }
    return false;
  }

  protected FeeOpReturnImpl findOpReturnImpl(byte[] opReturn) {
    for (FeeOpReturnImpl feeOpReturnImpl : feeOpReturnImpls) {
      if (feeOpReturnImpl.acceptsOpReturn(opReturn)) {
        return feeOpReturnImpl;
      }
    }
    log.error("Invalid opReturn.length: " + opReturn.length);
    return null;
  }

  public WhirlpoolFeeData decode(
      WhirlpoolFeeOutput feeOutput,
      BIP47Account secretAccountBip47V0,
      BIP47Account secretAccountBip47,
      TransactionOutPoint input0OutPoint,
      byte[] input0Pubkey)
      throws Exception {

    // decrypt feePayload
    byte[] opReturn = feeOutput.getOpReturnValue();
    FeeOpReturnImpl feeOpReturnImpl = findOpReturnImpl(opReturn);
    if (feeOpReturnImpl == null) {
      throw new Exception("Unknown FeeOpReturnImpl");
    }
    if (log.isDebugEnabled()) {
      log.debug("decode(): opReturnVersion=" + feeOpReturnImpl.getOpReturnVersion());
    }
    BIP47Account bip47Account =
        feeOpReturnImpl.getOpReturnVersion() == FeeOpReturnImplV0.OP_RETURN_VERSION
            ? secretAccountBip47V0
            : secretAccountBip47;
    FeeOpReturn feeOpReturn =
        feeOpReturnImpl.parseOpReturn(opReturn, bip47Account, input0OutPoint, input0Pubkey);

    // parse feePayload
    return parseFeePayload(feeOpReturn, feeOutput.getTxOutput());
  }

  protected WhirlpoolFeeData parseFeePayload(FeeOpReturn feeOpReturn, TransactionOutput txOutput)
      throws Exception {
    FeePayloadV1 fp = FeePayloadV1.parse(feeOpReturn.getFeePayload());
    int feeIndice = fp.getFeeIndice();
    short scodePayload = fp.getScodePayload();
    short feePartner = fp.getFeePartner();
    short feeOpReturnVersion = feeOpReturn.getOpReturnVersion();
    short feePayloadVersion = fp.getVersion();
    return new WhirlpoolFeeData(
        feeIndice, scodePayload, feePartner, txOutput, feeOpReturnVersion, feePayloadVersion);
  }

  // encode/decode bytes

  public byte[] computeFeePayload(int feeIndice, short scodePayload, short partner) {
    return feeOpReturnImplCurrent.computeFeePayload(feeIndice, scodePayload, partner);
  }
}
