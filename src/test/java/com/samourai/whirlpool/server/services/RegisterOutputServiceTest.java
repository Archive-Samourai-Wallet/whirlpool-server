package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.integration.AbstractMixIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class RegisterOutputServiceTest extends AbstractMixIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true);
  }

  @Test
  public void registerOutput_shouldSuccessWhenValid() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // blind bordereau
    byte[] bordereau = ClientUtils.generateBordereau();
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        registerInputAndConfirmInput(mix, username, 999, false, blindingParams, bordereau, null);

    // go REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assertions.assertEquals(0, mix.getReceiveAddresses().size());

    // REGISTER_OUTPUT
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereau, receiveAddress, bordereau);

    // VERIFY
    Assertions.assertEquals(1, mix.getReceiveAddresses().size()); // output registered
  }

  @Test
  public void registerOutput_shouldSuccessWhenValid_soroban() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();
    PaymentCode paymentCode = testUtils.generatePaymentCode();

    // blind bordereau
    byte[] bordereau = ClientUtils.generateBordereau();
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        registerInputAndConfirmInput(
            mix, username, 999, false, blindingParams, bordereau, paymentCode);

    // go REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assertions.assertEquals(0, mix.getReceiveAddresses().size());

    // REGISTER_OUTPUT
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereau, receiveAddress, bordereau);

    // VERIFY
    Assertions.assertEquals(1, mix.getReceiveAddresses().size()); // output registered
  }

  @Test
  public void registerOutput_shouldFailWhenBlindedBordereauFromInvalidPubkey() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // blind bordereau
    byte[] bordereau = ClientUtils.generateBordereau();
    RSAKeyParameters fakePublicKey = (RSAKeyParameters) cryptoService.generateKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(fakePublicKey); // blind from INVALID pubkey

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        registerInputAndConfirmInput(mix, username, 999, false, blindingParams, bordereau, null);

    // go REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assertions.assertEquals(0, mix.getReceiveAddresses().size());

    // TEST
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    try {
      registerOutputService.registerOutput(
          mix.computeInputsHash(), unblindedSignedBordereau, receiveAddress, bordereau);
      Assertions.assertTrue(false); // should throw exception
    } catch (Exception e) {
      // exception expected
      Assertions.assertEquals("Invalid unblindedSignedBordereau", e.getMessage());
    }

    // VERIFY
    Assertions.assertEquals(0, mix.getReceiveAddresses().size()); // output NOT registered
  }

  @Test
  public void registerOutput_shouldFailWhenBordereauFromPreviousMix() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // blind bordereau
    byte[] bordereau = ClientUtils.generateBordereau();
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // get signed blinded bordereau from a first mix
    byte[] signedBlindedBordereauFirstMix =
        registerInputAndConfirmInput(mix, username, 999, false, blindingParams, bordereau, null);

    // *** NEW MIX ***
    mixService.__reset();
    mix = __getCurrentMix();

    // register input again
    serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
    byte[] signedBlindedBordereauSecondMix =
        registerInputAndConfirmInput(mix, username, 999, false, blindingParams, bordereau, null);

    // go REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assertions.assertEquals(0, mix.getReceiveAddresses().size());

    // TEST: unblindedSignedBordereau from FIRST mix should be REJECTED
    byte[] unblindedSignedBordereauFirstMix =
        clientCryptoService.unblind(signedBlindedBordereauFirstMix, blindingParams);
    try {
      registerOutputService.registerOutput(
          mix.computeInputsHash(), unblindedSignedBordereauFirstMix, receiveAddress, bordereau);
      Assertions.assertTrue(false); // should throw exception
    } catch (Exception e) {
      // exception expected
      Assertions.assertEquals("Invalid unblindedSignedBordereau", e.getMessage());
    }

    // VERIFY
    Assertions.assertEquals(0, mix.getReceiveAddresses().size()); // output NOT registered

    // TEST: unblindedSignedBordereau from SECOND mix should be ACCEPTED
    byte[] unblindedSignedBordereauSecondMix =
        clientCryptoService.unblind(signedBlindedBordereauSecondMix, blindingParams);
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereauSecondMix, receiveAddress, bordereau);

    // VERIFY
    Assertions.assertEquals(1, mix.getReceiveAddresses().size()); // output registered
  }

  @Test
  public void registerOutput_shouldFailWhenInvalid() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // blind bordereau
    byte[] bordereau = ClientUtils.generateBordereau();
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // REGISTER_INPUT + CONFIRM_INPUT
    byte[] signedBlindedBordereau =
        registerInputAndConfirmInput(mix, username, 999, false, blindingParams, bordereau, null);

    // go REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assertions.assertEquals(0, mix.getReceiveAddresses().size());

    // REGISTER_OUTPUT
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);

    // - fail on invalid inputsHash
    try {
      registerOutputService.registerOutput(
          "invalidInputsHash",
          unblindedSignedBordereau,
          receiveAddress,
          bordereau); // INVALID inputsHash
      Assertions.assertTrue(false);
    } catch (Exception e) {
      Assertions.assertEquals("Mix failed", e.getMessage());
    }
    Assertions.assertEquals(0, mix.getReceiveAddresses().size()); // output NOT registered

    // - fail on invalid signedBordereau
    try {
      byte[] fakeSignedBordereau = "invalidBordereau".getBytes(); // INVALID signedBordereau
      registerOutputService.registerOutput(
          mix.computeInputsHash(), fakeSignedBordereau, receiveAddress, bordereau);
      Assertions.assertTrue(false);
    } catch (Exception e) {
      Assertions.assertEquals("Invalid unblindedSignedBordereau", e.getMessage());
    }

    // - fail on invalid bordereau
    try {
      byte[] fakeBordereau = "invalidBordereau".getBytes(); // INVALID bordereau
      registerOutputService.registerOutput(
          mix.computeInputsHash(),
          unblindedSignedBordereau,
          "tb1qnfhmn4vfgprfnsnz2fadfr48cquydjkz4wpfgq",
          fakeBordereau);
      Assertions.assertTrue(false);
    } catch (Exception e) {
      Assertions.assertEquals("Invalid unblindedSignedBordereau", e.getMessage());
    }

    // - success when valid
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereau, receiveAddress, bordereau);
    Assertions.assertEquals(1, mix.getReceiveAddresses().size()); // output registered
  }

  @Test
  public void registerOutput_shouldFailWhenReuseInputAddress() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";

    // blind bordereau
    byte[] bordereau = ClientUtils.generateBordereau();
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // REGISTER_INPUT + CONFIRM_INPUT
    byte[] signedBlindedBordereau =
        registerInputAndConfirmInput(
            mix, username, 999, false, blindingParams, bordereau, null); // reuse input address

    // go REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assertions.assertEquals(0, mix.getReceiveAddresses().size());

    // REGISTER_OUTPUT
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);

    // - fail on receiveAddress reuse from inputs
    try {
      String reusedAddress =
          mix.getInputs()._getInputs().iterator().next().getOutPoint().getToAddress();
      registerOutputService.registerOutput(
          mix.computeInputsHash(),
          unblindedSignedBordereau,
          reusedAddress,
          bordereau); // INVALID receiveAddress
      Assertions.assertTrue(false);
    } catch (Exception e) {
      Assertions.assertEquals("output already registered as input", e.getMessage());
    }
  }

  @Test
  public void registerOutput_shouldFailWhenReuseBordereau() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String username2 = "testusername2";

    // blind bordereau #1
    byte[] bordereau = ClientUtils.generateBordereau();
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // blind bordereau #2
    byte[] bordereau2 = ClientUtils.generateBordereau();
    RSABlindingParameters blindingParams2 =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // REGISTER_INPUT
    TxOutPoint outPoint = registerInput(mix, username, 999, true, null);

    // REGISTER_INPUT + CONFIRM_INPUT #2
    serverConfig.getRegisterInput().setMaxInputsSameUserHash(2);
    byte[] signedBlindedBordereau2 =
        registerInputAndConfirmInput(mix, username2, 999, false, blindingParams2, bordereau2, null);

    // CONFIRM_INPUT #1
    byte[] signedBlindedBordereau =
        confirmInput(
            mix, username, blindingParams, bordereau, outPoint.getHash(), outPoint.getIndex());

    Assertions.assertEquals(2, mix.getNbInputs());

    // go REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assertions.assertEquals(0, mix.getReceiveAddresses().size());

    // REGISTER_OUTPUT #1
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereau, receiveAddress, bordereau);

    // REGISTER_OUTPUT #2

    byte[] unblindedSignedBordereau2 =
        clientCryptoService.unblind(signedBlindedBordereau2, blindingParams2);
    String receiveAddress2 = testUtils.generateSegwitAddress().getBech32AsString();

    // should fail on reuse bordereau
    Exception e =
        Assertions.assertThrows(
            IllegalInputException.class,
            () ->
                registerOutputService.registerOutput(
                    mix.computeInputsHash(),
                    unblindedSignedBordereau.clone(),
                    receiveAddress2,
                    bordereau));
    Assertions.assertEquals("Bordereau already registered", e.getMessage());

    // should fail on reuse receiveAddress
    Exception ee =
        Assertions.assertThrows(
            IllegalInputException.class,
            () ->
                registerOutputService.registerOutput(
                    mix.computeInputsHash(),
                    unblindedSignedBordereau2,
                    receiveAddress,
                    bordereau2));
    Assertions.assertEquals("Output already registered", ee.getMessage());

    // should succeed on good bordereau
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereau2, receiveAddress2, bordereau2);
    Assertions.assertEquals(2, mix.getReceiveAddresses().size());
  }
}
