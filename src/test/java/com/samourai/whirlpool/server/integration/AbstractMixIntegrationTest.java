package com.samourai.whirlpool.server.integration;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.SorobanInput;
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
      SorobanInput sorobanInputOrNull)
      throws Exception {
    Pool pool = mix.getPool();
    String poolId = pool.getPoolId();

    ECKey ecKey = new ECKey();
    String signature = ecKey.signMessage(poolId);

    long inputBalance = pool.computePremixBalanceMax(liquidity);
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(
            new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters()),
            inputBalance,
            confirmations);

    registerInput(
        pool,
        username,
        signature,
        txOutPoint.getHash(),
        txOutPoint.getIndex(),
        liquidity,
        false,
        blockchainDataService.getBlockHeight(),
        sorobanInputOrNull);
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
      SorobanInput sorobanInputOrNull)
      throws Exception {
    int nbConfirming = mix.getNbConfirmingInputs();

    // REGISTER_INPUT
    TxOutPoint txOutPoint =
        registerInput(mix, username, confirmations, liquidity, sorobanInputOrNull);

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
    confirmInputService.confirmInput_webSocket(
        mixId, blindedBordereau, "userHash" + username, username);

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        cryptoService.signBlindedOutput(blindedBordereau, mix.getKeyPair());
    return signedBlindedBordereau;
  }
}
