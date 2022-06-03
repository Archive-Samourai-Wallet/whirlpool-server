package com.samourai.whirlpool.server.services;

import com.samourai.wallet.api.backend.beans.WalletResponse;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.whirlpool.client.wallet.data.chain.ChainSupplier;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import com.samourai.whirlpool.server.services.rpc.RpcRawTransactionResponse;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BlockchainDataService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private CryptoService cryptoService;
  private RpcClientService rpcClientService;
  private Bech32UtilGeneric bech32Util;
  private WhirlpoolServerConfig serverConfig;
  private Integer blockHeight;

  public BlockchainDataService(
      CryptoService cryptoService,
      RpcClientService rpcClientService,
      Bech32UtilGeneric bech32Util,
      WhirlpoolServerConfig serverConfig) {
    this.cryptoService = cryptoService;
    this.rpcClientService = rpcClientService;
    this.bech32Util = bech32Util;
    this.serverConfig = serverConfig;
    this.blockHeight = null;

    // fetch blockHeight on startup
    AsyncUtil.getInstance().runIOAsyncCompletable(() -> scheduleBlockHeight());
  }

  @Scheduled(fixedDelay = 120000)
  protected void scheduleBlockHeight() {
    try {
      int newBlockHeight = rpcClientService.getBlockHeight();
      if (blockHeight == null || blockHeight != newBlockHeight) {
        blockHeight = newBlockHeight;
        if (log.isDebugEnabled()) {
          log.debug("blockHeight: " + blockHeight);
        }
      }
    } catch (Exception e) {
      log.error("scheduleBlockHeight failed!", e);
      blockHeight = null;
    }
  }

  public boolean checkBlockHeight(long testBlockHeight) {
    if (blockHeight == null) {
      log.warn("checkBlockHeight: blockHeight not available");
      return true;
    }
    long spread = Math.abs(testBlockHeight - blockHeight);
    if (spread > serverConfig.getRpcClient().getBlockHeightMaxSpread()) {
      log.warn(
          "blockHeight rejected: spread="
              + spread
              + ", blockHeight="
              + blockHeight
              + ", testBlockHeight="
              + testBlockHeight);
      return false;
    } else if (spread > 1) {
      if (log.isDebugEnabled()) {
        log.debug(
            "blockHeight tolerance: spread="
                + spread
                + ", blockHeight="
                + blockHeight
                + ", testBlockHeight="
                + testBlockHeight);
      }
    }
    return true;
  }

  public Optional<RpcTransaction> getRpcTransaction(String txid) {
    if (log.isTraceEnabled()) {
      log.trace("RPC query: getRawTransaction " + txid);
    }
    Optional<RpcRawTransactionResponse> queryRawTxHex = rpcClientService.getRawTransaction(txid);
    if (!queryRawTxHex.isPresent()) {
      log.error("Tx not found: " + txid);
      return Optional.empty();
    }
    try {
      NetworkParameters params = cryptoService.getNetworkParameters();
      RpcTransaction rpcTx = new RpcTransaction(queryRawTxHex.get(), params);
      return Optional.of(rpcTx);
    } catch (Exception e) {
      log.error("Unable to parse RpcRawTransactionResponse", e);
      return Optional.empty();
    }
  }

  public Optional<TxOutPoint> getOutPoint(RpcTransaction rpcTransaction, long utxoIndex) {
    String utxoHash = rpcTransaction.getTx().getHashAsString();
    TransactionOutput txOutput = rpcTransaction.getTx().getOutput(utxoIndex);
    if (txOutput == null) {
      return Optional.empty();
    }

    long inputValue = txOutput.getValue().getValue();
    String toAddress =
        Utils.getToAddressBech32(txOutput, bech32Util, cryptoService.getNetworkParameters());
    TxOutPoint txOutPoint =
        new TxOutPoint(
            utxoHash,
            utxoIndex,
            inputValue,
            rpcTransaction.getConfirmations(),
            txOutput.getScriptBytes(),
            toAddress);
    return Optional.of(txOutPoint);
  }

  public boolean isTxOutUnspent(String txid, long utxoIndex) {
    return rpcClientService.isTxOutUnspent(txid, utxoIndex);
  }

  public Integer getBlockHeight() {
    return blockHeight;
  }

  public ChainSupplier computeChainSupplier() {
    ChainSupplier chainSupplier =
        () -> {
          WalletResponse.InfoBlock infoBlock = new WalletResponse.InfoBlock();
          infoBlock.height = getBlockHeight();
          return infoBlock;
        };
    return chainSupplier;
  }
}
