package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.wallet.api.backend.beans.BackendPushTxException;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.Tx0Validation;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PushService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private BackendService backendService;
  private Tx0ValidationService tx0ValidationService;

  public PushService(BackendService backendService, Tx0ValidationService tx0ValidationService)
      throws Exception {
    this.backendService = backendService;
    this.tx0ValidationService = tx0ValidationService;
  }

  public String validateAndPushTx0(byte[] txBytes, long txTime, Pool pool)
      throws Exception { // throws BackendPushTxException
    // validate tx0
    Tx0Validation tx0Validation;
    try {
      tx0Validation = tx0ValidationService.parseAndValidate(txBytes, txTime, pool);
    } catch (NotifiableException e) {
      // wrap "Not a TX0" as BackendPushTxException
      throw new BackendPushTxException(e.getMessage());
    }

    Transaction tx = tx0Validation.getTx();
    String txid = tx.getHashAsString();
    if (log.isDebugEnabled()) {
      log.info("pushing tx0: " + txid);
    }

    // push
    String txHex = TxUtil.getInstance().getTxHex(tx);
    Collection<Integer> strictModeVouts = tx0Validation.findStrictModeVouts();

    backendService.pushTx(txHex, strictModeVouts); // throws BackendPushTxException
    if (log.isDebugEnabled()) {
      log.debug("pushTx0 success: " + txid);
    }
    return txid;
  }
}
