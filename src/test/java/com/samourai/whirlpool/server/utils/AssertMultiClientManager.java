package com.samourai.whirlpool.server.utils;

import com.samourai.soroban.client.RpcWallet;
import com.samourai.soroban.client.RpcWalletImpl;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bipWallet.BipWallet;
import com.samourai.wallet.chain.ChainSupplier;
import com.samourai.wallet.crypto.CryptoUtil;
import com.samourai.wallet.hd.BIP_WALLET;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.wallet.beans.IndexRange;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientImpl;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.InputPool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.services.BlockchainDataService;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.services.rpc.MockRpcClientServiceImpl;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssertMultiClientManager extends MultiClientManager {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private TestUtils testUtils;
  private CryptoService cryptoService;
  private MockRpcClientServiceImpl rpcClientService;
  private BlockchainDataService blockchainDataService;
  private WhirlpoolClientConfig whirlpoolClientConfig;

  private Mix mix;

  private TxOutPoint[] inputs;
  private ECKey[] inputKeys;
  private BipWallet[] bip84Wallets;
  private NetworkParameters params;

  public AssertMultiClientManager(
      int nbClients,
      Mix mix,
      TestUtils testUtils,
      CryptoService cryptoService,
      MockRpcClientServiceImpl rpcClientService,
      BlockchainDataService blockchainDataService,
      WhirlpoolClientConfig whirlpoolClientConfig,
      NetworkParameters params) {
    this.mix = mix;
    this.testUtils = testUtils;
    this.cryptoService = cryptoService;
    this.rpcClientService = rpcClientService;
    this.blockchainDataService = blockchainDataService;
    this.whirlpoolClientConfig = whirlpoolClientConfig;

    inputs = new TxOutPoint[nbClients];
    inputKeys = new ECKey[nbClients];
    bip84Wallets = new BipWallet[nbClients];
    this.params = params;
  }

  private WhirlpoolClient createClient() {
    return new WhirlpoolClientImpl(whirlpoolClientConfig);
  }

  private int prepareClientWithMock(long inputBalance) throws Exception {
    SegwitAddress inputAddress = testUtils.generateSegwitAddress();
    BipWallet bip84Wallet = testUtils.generateWallet().getBip84Wallet(BIP_WALLET.DEPOSIT_BIP84);
    return prepareClientWithMock(inputAddress, bip84Wallet, null, null, inputBalance);
  }

  private int prepareClientWithMock(
      SegwitAddress inputAddress,
      BipWallet bip84Wallet,
      Integer nbConfirmations,
      Integer utxoIndex,
      long inputBalance)
      throws Exception {

    if (utxoIndex == null) {
      utxoIndex = 0;
    }
    Integer nbOuts = utxoIndex + 1;

    // prepare input & output and mock input
    RpcTransaction rpcTransaction =
        rpcClientService.createAndMockTx(inputAddress, inputBalance, nbConfirmations, nbOuts);
    TxOutPoint utxo = blockchainDataService.getOutPoint(rpcTransaction, utxoIndex).get();
    ECKey utxoKey = inputAddress.getECKey();

    return prepareClient(utxo, utxoKey, bip84Wallet);
  }

  private synchronized int prepareClient(TxOutPoint utxo, ECKey utxoKey, BipWallet bip84Wallet) {
    int i = clients.size();
    register(createClient());
    bip84Wallets[i] = bip84Wallet;
    inputs[i] = utxo;
    inputKeys[i] = utxoKey;
    return i;
  }

  public void connectWithMockOrFail(boolean liquidity) {
    long premixBalanceMin = mix.getPool().computePremixBalanceMin(liquidity);
    connectWithMockOrFail(premixBalanceMin);
  }

  public void connectWithMockOrFail(long inputBalance) {
    try {
      connectWithMock(inputBalance);
    } catch (Exception e) {
      log.error("", e);
      Assertions.assertTrue(false);
    }
  }

  public void connectWithMock(long inputBalance) throws Exception {
    int i = prepareClientWithMock(inputBalance);
    whirlpool(i);
  }

  public void connectWithMock(
      SegwitAddress inputAddress,
      BipWallet bip84Wallet,
      Integer nbConfirmations,
      Integer utxoIndex,
      long inputBalance)
      throws Exception {
    int i =
        prepareClientWithMock(inputAddress, bip84Wallet, nbConfirmations, utxoIndex, inputBalance);
    whirlpool(i);
  }

  public void connect(TxOutPoint utxo, ECKey utxoKey, BipWallet bip84Wallet) {
    int i = prepareClient(utxo, utxoKey, bip84Wallet);
    whirlpool(i);
  }

  private void whirlpool(int i) {
    Pool pool = mix.getPool();
    WhirlpoolClient whirlpoolClient = clients.get(i);
    MultiClientListener listener = listeners.get(i);
    TxOutPoint input = inputs[i];
    ECKey ecKey = inputKeys[i];

    BipWallet bip84Wallet = bip84Wallets[i];
    UtxoWithBalance utxo = input.toUtxoWithBalance();
    IPremixHandler premixHandler =
        new PremixHandler(utxo, ecKey, "userPreHash" + input.getHash() + input.getIndex());
    IPostmixHandler postmixHandler = new Bip84PostmixHandler(params, bip84Wallet, IndexRange.EVEN);
    ChainSupplier chainSupplier = blockchainDataService.computeChainSupplier();
    BIP47Wallet bip47Wallet = new BIP47Wallet(bip84Wallet.getHdWallet());
    CryptoUtil cryptoUtil = CryptoUtil.getInstanceJava();
    RpcWallet rpcWallet = new RpcWalletImpl(bip47Wallet, cryptoUtil);
    MixParams mixParams =
        new MixParams(
            pool.getPoolId(),
            pool.getDenomination(),
            pool.computeMustMixBalanceMin(),
            pool.computeMustMixBalanceMax(),
            null,
            premixHandler,
            postmixHandler,
            chainSupplier,
            rpcWallet);

    whirlpoolClient.whirlpool(mixParams, listener);
  }

  private void waitRegisteredInputs(int nbInputsExpected) throws Exception {
    int MAX_WAITS = 5;
    int WAIT_DURATION = 4000;
    for (int i = 0; i < MAX_WAITS; i++) {
      String msg =
          "# ("
              + (i + 1)
              + "/"
              + MAX_WAITS
              + ") Waiting for registered inputs: "
              + mix.getNbInputs()
              + "/"
              + nbInputsExpected;
      if (mix.getNbInputs() != nbInputsExpected) {
        log.info(msg + " : waiting longer...");
        Thread.sleep(WAIT_DURATION);
      } else {
        log.info(msg + " : success");
        return;
      }
    }

    // debug on failure
    log.info(
        "# (LAST) Waiting for registered inputs: " + mix.getNbInputs() + " " + nbInputsExpected);
    Assertions.assertEquals(nbInputsExpected, mix.getNbInputs());
  }

  public void waitLiquiditiesInPool(int nbLiquiditiesInPoolExpected) throws Exception {
    InputPool liquidityPool = mix.getPool().getLiquidityQueue();

    int MAX_WAITS = 5;
    int WAIT_DURATION = 4000;
    for (int i = 0; i < MAX_WAITS; i++) {
      String msg =
          "# ("
              + (i + 1)
              + "/"
              + MAX_WAITS
              + ") Waiting for liquidities in pool: "
              + liquidityPool.getSize()
              + " vs "
              + nbLiquiditiesInPoolExpected;
      if (liquidityPool.getSize() != nbLiquiditiesInPoolExpected) {
        log.info(msg + " : waiting longer...");
        Thread.sleep(WAIT_DURATION);
      } else {
        log.info(msg + " : success");
        return;
      }
    }

    // debug on failure
    log.info(
        "# (LAST) Waiting for liquidities in pool: "
            + liquidityPool.getSize()
            + " vs "
            + nbLiquiditiesInPoolExpected);
    Assertions.assertEquals(nbLiquiditiesInPoolExpected, liquidityPool.getSize());
  }

  public void waitMixStatus(MixStatus mixStatusExpected) throws Exception {
    int MAX_WAITS = 5;
    int WAIT_DURATION = 4000;
    for (int i = 0; i < MAX_WAITS; i++) {
      String msg =
          "# ("
              + (i + 1)
              + "/"
              + MAX_WAITS
              + ") Waiting for mixStatus: "
              + mix.getMixStatus()
              + " vs "
              + mixStatusExpected;
      if (!mix.getMixStatus().equals(mixStatusExpected)) {
        log.info(msg + " : waiting longer...");
        Thread.sleep(WAIT_DURATION);
      } else {
        log.info(msg + " : success");
        return;
      }
    }

    log.info("# (LAST) Waiting for mixStatus: " + mix.getMixStatus() + " vs " + mixStatusExpected);
    Assertions.assertEquals(mixStatusExpected, mix.getMixStatus());
  }

  public void setMixNext() {
    Mix nextMix = mix.getPool().getCurrentMix();
    Assertions.assertNotEquals(mix, nextMix);
    this.mix = nextMix;
    log.info("============= NEW MIX DETECTED: " + nextMix.getMixId() + " =============");
  }

  public void assertMixStatusConfirmInput(int nbInputsExpected, boolean hasLiquidityExpected)
      throws Exception {
    // wait inputs to register
    waitRegisteredInputs(nbInputsExpected);

    InputPool liquidityPool = mix.getPool().getLiquidityQueue();
    System.out.println("=> mixStatus=" + mix.getMixStatus() + ", nbInputs=" + mix.getNbInputs());

    // all clients should have registered their outputs
    Assertions.assertEquals(MixStatus.CONFIRM_INPUT, mix.getMixStatus());
    Assertions.assertEquals(nbInputsExpected, mix.getNbInputs());
    Assertions.assertEquals(hasLiquidityExpected, liquidityPool.hasInputs());
  }

  public void assertMixStatusSuccess(int nbAllRegisteredExpected, boolean hasLiquidityExpected)
      throws Exception {
    // wait inputs to register
    waitRegisteredInputs(nbAllRegisteredExpected);

    Thread.sleep(2000);

    // mix automatically switches to REGISTER_OUTPUTS, then SIGNING, then SUCCESS
    waitMixStatus(MixStatus.SUCCESS);
    Assertions.assertEquals(MixStatus.SUCCESS, mix.getMixStatus());
    Assertions.assertEquals(nbAllRegisteredExpected, mix.getNbInputs());

    InputPool liquidityPool = mix.getPool().getLiquidityQueue();
    Assertions.assertEquals(hasLiquidityExpected, liquidityPool.hasInputs());

    // all clients should have registered their outputs
    Assertions.assertEquals(nbAllRegisteredExpected, mix.getReceiveAddresses().size());

    // all clients should have signed
    Assertions.assertEquals(nbAllRegisteredExpected, mix.getNbSignatures());

    // all clients should be SUCCESS
    assertClientsSuccess(nbAllRegisteredExpected);
  }

  public void assertMixTx(String expectedTxHash, String expectedTxHex) {
    Transaction tx = mix.getTx();
    String txHash = tx.getHashAsString();
    String txHex = TxUtil.getInstance().getTxHex(tx);
    Assertions.assertEquals(expectedTxHash, txHash);
    Assertions.assertEquals(expectedTxHex, txHex);
  }

  private void assertClientsSuccess(int nbSuccessExpected) {
    waitDone(nbSuccessExpected);
    Assertions.assertTrue(getNbSuccess() == nbSuccessExpected);
  }

  public void exit() {
    for (WhirlpoolClient whirlpoolClient : clients) {
      if (whirlpoolClient != null) {
        whirlpoolClient.stop(true);
      }
    }
  }
}
