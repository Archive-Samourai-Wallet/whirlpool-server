package com.samourai.whirlpool.server.integration;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.services.*;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractMixIntegrationTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired protected RegisterInputService registerInputService;

  @Autowired protected ConfirmInputService confirmInputService;

  @Autowired protected RegisterOutputService registerOutputService;

  protected TxOutPoint registerInput(
      Mix mix,
      String username,
      int confirmations,
      boolean liquidity,
      PaymentCode sorobanPaymentCodeOrNull)
      throws Exception {
    String poolId = mix.getPool().getPoolId();

    ECKey ecKey = new ECKey();
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computePremixBalanceMin(liquidity);
    if (!liquidity) {
      inputBalance += +mix.getPool().getMinerFeeMix();
    }
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(
            new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters()),
            inputBalance,
            confirmations);

    registerInputService.registerInput(
        poolId,
        username,
        signature,
        txOutPoint.getHash(),
        txOutPoint.getIndex(),
        liquidity,
        false,
        blockchainDataService.getBlockHeight(),
        sorobanPaymentCodeOrNull,
        null);
    waitMixLimitsService(mix);
    return txOutPoint;
  }

  protected RSABlindingParameters computeBlindingParams(Mix mix) {
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);
    return blindingParams;
  }

  protected byte[] registerInputAndConfirmInput(
      Mix mix,
      String username,
      int confirmations,
      boolean liquidity,
      RSABlindingParameters blindingParams,
      byte[] bordereau,
      PaymentCode sorobanPaymentCodeOrNull)
      throws Exception {
    int nbConfirming = mix.getNbConfirmingInputs();

    // REGISTER_INPUT
    TxOutPoint txOutPoint =
        registerInput(mix, username, confirmations, liquidity, sorobanPaymentCodeOrNull);

    boolean queued = (mix.getNbConfirmingInputs() == nbConfirming);
    if (queued) {
      if (log.isDebugEnabled()) {
        log.debug("Not confirming input: it was queued");
      }
      return null;
    }

    return confirmInput(
        mix, username, blindingParams, bordereau, txOutPoint.getHash(), txOutPoint.getIndex());
  }

  public byte[] confirmInput(
      Mix mix,
      String username,
      RSABlindingParameters blindingParams,
      byte[] bordereau,
      String utxoHash,
      long utxoIndex)
      throws Exception {
    // blind bordereau
    if (blindingParams == null) {
      blindingParams = computeBlindingParams(mix);
    }
    if (bordereau == null) {
      bordereau = ClientUtils.generateBordereau();
    }
    byte[] blindedBordereau = clientCryptoService.blind(bordereau, blindingParams);

    // CONFIRM_INPUT
    String mixId = mix.getMixId();
    confirmInputService.confirmInputOrQueuePool(
        mixId, username, blindedBordereau, "userHash" + username, utxoHash, utxoIndex);

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        cryptoService.signBlindedOutput(blindedBordereau, mix.getKeyPair());
    return signedBlindedBordereau;
  }
}
