package com.samourai.whirlpool.server.utils;

import com.samourai.wallet.api.backend.beans.UnspentOutput;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactoryGeneric;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.send.provider.SimpleUtxoKeyProvider;
import com.samourai.wallet.util.CryptoTestUtil;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.services.CryptoService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutPoint;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TestUtils {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private CryptoService cryptoService;
  protected Bech32UtilGeneric bech32Util;
  protected HD_WalletFactoryGeneric hdWalletFactory;
  private CryptoTestUtil cryptoTestUtil;

  public TestUtils(
      CryptoService cryptoService,
      Bech32UtilGeneric bech32Util,
      HD_WalletFactoryGeneric hdWalletFactory,
      CryptoTestUtil cryptoTestUtil) {
    this.cryptoService = cryptoService;
    this.bech32Util = bech32Util;
    this.hdWalletFactory = hdWalletFactory;
    this.cryptoTestUtil = cryptoTestUtil;
  }

  public SegwitAddress generateSegwitAddress() {
    return cryptoTestUtil.generateSegwitAddress(cryptoService.getNetworkParameters());
  }

  public BIP47WalletAndHDWallet generateWallet(byte[] seed, String passphrase) throws Exception {
    // init BIP44 wallet
    HD_Wallet inputWallet =
        hdWalletFactory.getHD(44, seed, passphrase, cryptoService.getNetworkParameters());

    // init BIP47 wallet
    BIP47Wallet bip47InputWallet = new BIP47Wallet(inputWallet);

    return new BIP47WalletAndHDWallet(bip47InputWallet, inputWallet);
  }

  public BIP47WalletAndHDWallet generateWallet() throws Exception {
    byte seed[] = cryptoTestUtil.generateSeed();
    return generateWallet(seed, "test");
  }

  public void assertPool(int nbMustMix, int nbLiquidity, Pool pool) {
    Assertions.assertEquals(nbMustMix, pool.getMustMixQueue().getSize());
    Assertions.assertEquals(nbLiquidity, pool.getLiquidityQueue().getSize());
  }

  public void assertPoolEmpty(Pool pool) {
    assertPool(0, 0, pool);
  }

  public void assertMix(int nbInputsConfirmed, int confirming, Mix mix) {
    Assertions.assertEquals(nbInputsConfirmed, mix.getNbInputs());
    Assertions.assertEquals(confirming, mix.getNbConfirmingInputs());
  }

  public void assertMix(int nbInputs, Mix mix) {
    assertMix(nbInputs, 0, mix);
  }

  public void assertMixEmpty(Mix mix) {
    assertMix(0, mix);
  }

  public AsymmetricCipherKeyPair readPkPEM(String pkPem) throws Exception {
    PemReader pemReader =
        new PemReader(new InputStreamReader(new ByteArrayInputStream(pkPem.getBytes())));
    PemObject pemObject = pemReader.readPemObject();

    RSAPrivateCrtKeyParameters privateKeyParams =
        (RSAPrivateCrtKeyParameters) PrivateKeyFactory.createKey(pemObject.getContent());
    return new AsymmetricCipherKeyPair(privateKeyParams, privateKeyParams); // TODO
  }

  public String computePkPEM(AsymmetricCipherKeyPair keyPair) throws Exception {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    PemWriter writer = new PemWriter(new OutputStreamWriter(os));

    PrivateKeyInfo pkInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(keyPair.getPrivate());

    writer.writeObject(new PemObject("PRIVATE KEY", pkInfo.getEncoded()));
    writer.flush();
    writer.close();
    String pem = new String(os.toByteArray());
    return pem;
  }

  public ConfirmedInput computeConfirmedInput(
      String poolId, String utxoHash, long utxoIndex, boolean liquidity) {
    TxOutPoint outPoint = new TxOutPoint(utxoHash, utxoIndex, 1234, 99, null, "fakeReceiveAddress");
    RegisteredInput registeredInput =
        new RegisteredInput(poolId, "foo", liquidity, outPoint, "127.0.0.1", null);
    ConfirmedInput confirmedInput =
        new ConfirmedInput(registeredInput, "userHash" + utxoHash + utxoIndex);
    return confirmedInput;
  }

  public UnspentOutput computeUnspentOutput(String hash, int index, long value, String toAddress)
      throws Exception {
    NetworkParameters params = cryptoService.getNetworkParameters();
    String scriptBytes =
        Hex.toHexString(Bech32UtilGeneric.getInstance().computeScriptPubKey(toAddress, params));
    UnspentOutput spendFrom = new UnspentOutput();
    spendFrom.tx_hash = hash;
    spendFrom.tx_output_n = index;
    spendFrom.value = value;
    spendFrom.script = scriptBytes;
    spendFrom.addr = toAddress;
    spendFrom.confirmations = 1234;
    spendFrom.xpub = new UnspentOutput.Xpub();
    spendFrom.xpub.path = "foo";
    return spendFrom;
  }

  public UnspentOutput computeUnspentOutput(TransactionOutPoint outPoint, String toAddress)
      throws Exception {
    return computeUnspentOutput(
        outPoint.getHash().toString(),
        (int) outPoint.getIndex(),
        outPoint.getValue().value,
        toAddress);
  }

  public UnspentOutput generateUnspentOutputWithKey(
      long value, NetworkParameters params, SimpleUtxoKeyProvider utxoKeyProvider)
      throws Exception {
    ECKey input0Key = new ECKey();
    String input0OutPointAddress = new SegwitAddress(input0Key, params).getBech32AsString();
    TransactionOutPoint input0OutPoint =
        cryptoTestUtil.generateTransactionOutPoint(input0OutPointAddress, value, params);
    UnspentOutput utxo = computeUnspentOutput(input0OutPoint, input0OutPointAddress);
    utxoKeyProvider.setKey(input0OutPoint, input0Key);
    return utxo;
  }
}
