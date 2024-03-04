package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.client.mix.handler.PremixHandler;
import com.samourai.whirlpool.client.mix.handler.UtxoWithBalance;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.MixStatus;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class SigningServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired private RegisterInputService registerInputService;

  @Autowired private ConfirmInputService confirmInputService;

  @Autowired private RegisterOutputService registerOutputService;

  @Autowired private SigningService signingService;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true); // TODO
  }

  @Test
  public void signing_success() throws Exception {
    // mix config
    Mix mix = __nextMix(1, 0, 1, __getCurrentPoolId()); // 1 user

    // prepare input
    ECKey ecKey = new ECKey();
    boolean liquidity = false;
    long inputBalance = mix.getPool().computePremixBalanceMax(liquidity);
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(new SegwitAddress(ecKey.getPubKey(), params), inputBalance, 10);

    // valid signature
    UtxoWithBalance utxoWithBalance = txOutPoint.toUtxoWithBalance();
    PremixHandler premixHandler = new PremixHandler(utxoWithBalance, ecKey, "userPreHash");

    // test
    String username = "user1";
    String[] witness64 = doSigning(mix, premixHandler, liquidity, txOutPoint, username);
    signingService.signing_webSocket(mix.getMixId(), witness64, username);

    // verify
    Assertions.assertEquals(MixStatus.SUCCESS, mix.getMixStatus());
  }

  @Test
  public void signing_failOnUnknownUsername() throws Exception {
    // mix config
    Mix mix = __nextMix(1, 0, 1, __getCurrentPoolId()); // 1 user

    // prepare input
    ECKey ecKey = new ECKey();
    boolean liquidity = false;
    long inputBalance = mix.getPool().computePremixBalanceMin(liquidity);
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(new SegwitAddress(ecKey.getPubKey(), params), inputBalance, 10);

    // valid signature
    UtxoWithBalance utxoWithBalance = txOutPoint.toUtxoWithBalance();
    PremixHandler premixHandler = new PremixHandler(utxoWithBalance, ecKey, "userPreHash");

    // test
    try {
      String username = "user1";
      String[] witness64 = doSigning(mix, premixHandler, liquidity, txOutPoint, username);
      signingService.signing_webSocket(mix.getMixId(), witness64, "dummy"); // invalid user
      Assertions.assertTrue(false);
    } catch (IllegalInputException e) {
      // verify
      Assertions.assertEquals("Input not found for signing username=dummy", e.getMessage());
      Assertions.assertEquals(MixStatus.SIGNING, mix.getMixStatus());
      return;
    }
    Assertions.assertTrue(false); // IllegalInputException expected
  }

  @Test
  public void signing_failOnDuplicate() throws Exception {
    // mix config
    Mix mix = __nextMix(2, 0, 2, __getCurrentPoolId()); // 2 users
    boolean liquidity = false;
    long inputBalance = mix.getPool().computePremixBalanceMin(liquidity);

    // trick to simulate one first user registered
    byte[] bordereau = ClientUtils.generateBordereau();
    String firstUsername = "firstUser";
    TxOutPoint firstTxOutPoint =
        createAndMockTxOutPoint(testUtils.generateSegwitAddress(), inputBalance, 10);
    mix.registerInput(
        new RegisteredInput(
            mix.getPool().getPoolId(),
            firstUsername,
            false,
            firstTxOutPoint,
            false,
            "userHash1",
            null),
        bordereau);
    mix.registerOutput(testUtils.generateSegwitAddress().getBech32AsString(), bordereau);

    // prepare input
    ECKey ecKey = new ECKey();
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(new SegwitAddress(ecKey.getPubKey(), params), inputBalance, 10);

    // valid signature
    UtxoWithBalance utxoWithBalance = txOutPoint.toUtxoWithBalance();
    PremixHandler premixHandler = new PremixHandler(utxoWithBalance, ecKey, "userPreHash");

    // test
    String username = "user1";
    String[] witness64 = doSigning(mix, premixHandler, liquidity, txOutPoint, username);
    signingService.signing_webSocket(mix.getMixId(), witness64, username); // valid
    try {
      signingService.signing_webSocket(mix.getMixId(), witness64, username); // duplicate signing
      Assertions.assertTrue(false);
    } catch (IllegalInputException e) {
      // verify
      Assertions.assertEquals("User already signed, username=" + username, e.getMessage());
      Assertions.assertEquals(MixStatus.SIGNING, mix.getMixStatus());
      return;
    }
    Assertions.assertTrue(false); // IllegalInputException expected
  }

  @Test
  public void signing_failOnInvalidSignature() throws Exception {
    // mix config
    Mix mix = __nextMix(1, 0, 1, __getCurrentPoolId()); // 1 user

    // prepare input
    ECKey ecKey = new ECKey();
    boolean liquidity = false;
    long inputBalance = mix.getPool().computePremixBalanceMin(liquidity);
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(new SegwitAddress(ecKey.getPubKey(), params), inputBalance, 10);

    // invalid signature (invalid key)
    UtxoWithBalance utxoWithBalance = txOutPoint.toUtxoWithBalance();
    PremixHandler premixHandler =
        new PremixHandler(utxoWithBalance, new ECKey(), "userPreHash"); // invalid key

    // test
    try {
      String username = "user1";
      String[] witness64 = doSigning(mix, premixHandler, liquidity, txOutPoint, username);
      signingService.signing_webSocket(mix.getMixId(), witness64, username);
      Assertions.assertTrue(false);
    } catch (IllegalInputException e) {
      // verify
      Assertions.assertEquals("Invalid signature", e.getMessage());
      return;
    }
    Assertions.assertTrue(false); // IllegalInputException expected
  }

  private String[] doSigning(
      Mix mix,
      PremixHandler premixHandler,
      boolean liquidity,
      TxOutPoint txOutPoint,
      String username)
      throws Exception {
    String mixId = mix.getMixId();
    Pool pool = mix.getPool();
    String poolId = pool.getPoolId();

    // register input
    String signature = premixHandler.signMessage(poolId);
    RegisteredInput registeredInput =
        registerInput(
            pool,
            username,
            signature,
            txOutPoint.getHash(),
            txOutPoint.getIndex(),
            liquidity,
            false,
            blockchainDataService.getBlockHeight(),
            null);
    waitMixLimitsService(mix);

    // confirm input
    mix.registerConfirmingInput(registeredInput);

    byte[] bordereau = ClientUtils.generateBordereau();
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();
    byte[] blindedBordereau = clientCryptoService.blind(bordereau, blindingParams);
    byte[] signedBlindedBordereau =
        confirmInputService
            .confirmInput_webSocket(mixId, blindedBordereau, "userHash" + username, username)
            .get();

    // register output
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    registerOutputService.registerOutput(mix, unblindedSignedBordereau, receiveAddress, bordereau);

    // signing
    Transaction txToSign = new Transaction(params, mix.getTx().bitcoinSerialize());
    int inputIndex =
        TxUtil.getInstance().findInputIndex(txToSign, txOutPoint.getHash(), txOutPoint.getIndex());
    premixHandler.signTransaction(txToSign, inputIndex, params);
    txToSign.verify();
    String[] witness64 = ClientUtils.witnessSerialize64(txToSign.getWitness(inputIndex));
    return witness64;
  }
}
