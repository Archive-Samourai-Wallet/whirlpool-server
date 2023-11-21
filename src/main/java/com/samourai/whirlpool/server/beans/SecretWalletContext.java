package com.samourai.whirlpool.server.beans;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactoryGeneric;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecretWalletContext {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final HD_WalletFactoryGeneric hdWalletFactory =
      HD_WalletFactoryGeneric.getInstance();
  private static final Bip47UtilJava bip47Util = Bip47UtilJava.getInstance();

  private BIP47Wallet bip47Wallet;
  private PaymentCode paymentCode;
  private HD_Address address;
  private String addressString;

  public SecretWalletContext(
      WhirlpoolServerConfig.SecretWalletConfig secretWalletConfig, NetworkParameters params)
      throws Exception {
    HD_Wallet bip44w =
        hdWalletFactory.restoreWallet(
            secretWalletConfig.getWords(), secretWalletConfig.getPassphrase(), params);
    bip47Wallet = new BIP47Wallet(bip44w);
    paymentCode = bip47Util.getPaymentCode(bip47Wallet);
    address = bip44w.getAddressAt(0, 0, 0);
    addressString = address.getAddressString();
  }

  public BIP47Wallet getBip47Wallet() {
    return bip47Wallet;
  }

  public PaymentCode getPaymentCode() {
    return paymentCode;
  }

  public HD_Address getAddress() {
    return address;
  }

  public String getAddressString() {
    return addressString;
  }
}