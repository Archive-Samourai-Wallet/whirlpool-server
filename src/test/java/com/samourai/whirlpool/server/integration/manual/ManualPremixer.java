package com.samourai.whirlpool.server.integration.manual;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32Segwit;
import com.samourai.wallet.util.FormatsUtilGeneric;
import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

public class ManualPremixer {

    // parameters
    private final NetworkParameters params;
    private int nbMixes;

    // init results
    private HashMap<String, HD_Wallet> wallets;
    public HashMap<String, BIP47Wallet> bip47Wallets;
    private HashMap<String, JSONObject> payloads;

    // premix results
    public HashMap<String,String> mixables;
    public HashMap<String, ECKey> toPrivKeys;
    public HashMap<String, String> toUTXO;
    public BigInteger biUnitSpendAmount;
    public BigInteger biUnitReceiveAmount;
    public long fee;

    public ManualPremixer(NetworkParameters params, int nbMixes) {
        this.params = params;
        this.nbMixes = nbMixes;
    }

    public void initWallets() throws Exception {
        final String BIP39_ENGLISH_SHA256 = "ad90bf3beb7b0eb7e5acd74727dc0da96e0a280a258354e7293fb7e211ac03db";
        wallets = new HashMap<String, HD_Wallet>();
        bip47Wallets = new HashMap<String, BIP47Wallet>();
        payloads = new HashMap<String, JSONObject>();
        InputStream wis = HD_Wallet.class.getResourceAsStream("/en_US.txt");
        if (wis != null) {
            MnemonicCode mc = new MnemonicCode(wis, BIP39_ENGLISH_SHA256);

            List<String> words = Arrays.asList("all all all all all all all all all all all all".split("\\s+"));
            byte[] seed = mc.toEntropy(words);

            //
            // create 5 wallets
            //
            for (int i = 0; i < nbMixes; i++) {
                // init BIP44 wallet
                HD_Wallet hdw = new HD_Wallet(44, mc, params, seed, "all" + Integer.toString(10 + i), 1);
                // init BIP84 wallet for input
                HD_Wallet hdw84 = new HD_Wallet(84, mc, params, Hex.decode(hdw.getSeedHex()), hdw.getPassphrase(), 1);
                // init BIP47 wallet for input
                BIP47Wallet bip47w = new BIP47Wallet(47, mc, params, Hex.decode(hdw.getSeedHex()), hdw.getPassphrase(), 1);

                //
                // collect addresses for tx0 utxos
                //
                String tx0spendFrom = new SegwitAddress(hdw84.getAccount(0).getChain(0).getAddressAt(0).getPubKey(), params).getBech32AsString();
                System.out.println("tx0 spend address:" + tx0spendFrom);

                //
                // collect wallet payment codes
                //
                String pcode = bip47w.getAccount(0).getPaymentCode();
                wallets.put(pcode, hdw84);
                bip47Wallets.put(pcode, bip47w);

                JSONObject payloadObj = new JSONObject();
                payloadObj.put("pcode", pcode);
                payloads.put(pcode, payloadObj);

            }

        }
        wis.close();
    }


    public void premix(Map<String,String> utxos, long swFee, long selectedAmount, long unitSpendAmount, long unitReceiveAmount, long fee) throws Exception {
        boolean isTestnet = FormatsUtilGeneric.getInstance().isTestNet(params);
        int feeIdx  = 0; // address index, in prod get index from Samourai API

        System.out.println("tx0: -------------------------------------------");

        // net miner's fee
        // fee = unitSpendAmount - unitReceiveAmount;

        BigInteger biSelectedAmount = BigInteger.valueOf(selectedAmount);
        biUnitSpendAmount = BigInteger.valueOf(unitSpendAmount);
        biUnitReceiveAmount = BigInteger.valueOf(unitReceiveAmount);
        BigInteger biSWFee = BigInteger.valueOf(swFee);
        BigInteger biChange = BigInteger.valueOf(selectedAmount - ((unitSpendAmount * nbMixes) + fee + swFee));

        mixables = new HashMap<String,String>();

        List<HD_Wallet> _wallets = new ArrayList<HD_Wallet>();
        _wallets.addAll(wallets.values());
        List<BIP47Wallet> _bip47Wallets = new ArrayList<BIP47Wallet>();
        _bip47Wallets.addAll(bip47Wallets.values());

        toPrivKeys = new HashMap<String, ECKey>();
        toUTXO = new HashMap<String, String>();

        //
        // tx0
        //
        for(int i = 0; i < nbMixes; i++)   {
            // init BIP84 wallet for input
            HD_Wallet hdw84 = _wallets.get(i);
            // init BIP47 wallet for input
            BIP47Wallet bip47w = _bip47Wallets.get(i);

            String tx0spendFrom = new SegwitAddress(hdw84.getAccount(0).getChain(0).getAddressAt(0).getPubKey(), params).getBech32AsString();
            ECKey ecKeySpendFrom = hdw84.getAccount(0).getChain(0).getAddressAt(0).getECKey();
            System.out.println("tx0 spend address:" + tx0spendFrom);
            String tx0change = new SegwitAddress(hdw84.getAccount(0).getChain(1).getAddressAt(0).getPubKey(), params).getBech32AsString();
            System.out.println("tx0 change address:" + tx0change);

            String pcode = bip47w.getAccount(0).getPaymentCode();
            JSONObject payloadObj = payloads.get(pcode);
            payloadObj.put("tx0change", tx0change);
            payloadObj.put("tx0utxo", utxos.get(tx0spendFrom));
            JSONArray spendTos = new JSONArray();
            for(int j = 0; j < nbMixes; j++)   {
                String toAddress = new SegwitAddress(hdw84.getAccountAt(Integer.MAX_VALUE - 2).getChain(0).getAddressAt(j).getPubKey(), params).getBech32AsString();
                toPrivKeys.put(toAddress, hdw84.getAccountAt(Integer.MAX_VALUE - 2).getChain(0).getAddressAt(j).getECKey());
//                System.out.println("spend to:"  + toAddress + "," + hdw84.getAccountAt(Integer.MAX_VALUE - 2).getChain(0).getAddressAt(j).getECKey().getPrivateKeyAsWiF(params));
                spendTos.put(toAddress);
                mixables.put(toAddress, pcode);
            }
            payloadObj.put("spendTos", spendTos);
//            System.out.println("payload:"  + payloadObj.toString());
            payloads.put(pcode, payloadObj);

            //
            // make tx:
            // 5 spendTo outputs
            // SW fee
            // change
            // OP_RETURN
            //
            List<TransactionOutput> outputs = new ArrayList<TransactionOutput>();
            Transaction tx = new Transaction(params);

            //
            // 5 spend outputs
            //
            for(int k = 0; k < spendTos.length(); k++)   {
                Pair<Byte, byte[]> pair = Bech32Segwit.decode(isTestnet ? "tb" : "bc", (String)spendTos.get(k));
                byte[] scriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());

                TransactionOutput txOutSpend = new TransactionOutput(params, null, Coin.valueOf(biUnitSpendAmount.longValue()), scriptPubKey);
                outputs.add(txOutSpend);
            }

            //
            // 1 change output
            //
            Pair<Byte, byte[]> pair = Bech32Segwit.decode(isTestnet ? "tb" : "bc", tx0change);
            byte[] _scriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());
            TransactionOutput txChange = new TransactionOutput(params, null, Coin.valueOf(biChange.longValue()), _scriptPubKey);
            outputs.add(txChange);

            // derive fee address
            final String XPUB_FEES = "vpub5YS8pQgZKVbrSn9wtrmydDWmWMjHrxL2mBCZ81BDp7Z2QyCgTLZCrnBprufuoUJaQu1ZeiRvUkvdQTNqV6hS96WbbVZgweFxYR1RXYkBcKt";
            DeterministicKey mKey = FormatsUtilGeneric.getInstance().createMasterPubKeyFromXPub(XPUB_FEES);
            DeterministicKey cKey = HDKeyDerivation.deriveChildKey(mKey, new ChildNumber(0, false)); // assume external/receive chain
            DeterministicKey adk = HDKeyDerivation.deriveChildKey(cKey, new ChildNumber(feeIdx, false));
            ECKey feeECKey = ECKey.fromPublicOnly(adk.getPubKey());
            String feeAddress = new SegwitAddress(feeECKey.getPubKey(), params).getBech32AsString();
            System.out.println("fee address:" + feeAddress);

            Script outputScript = ScriptBuilder.createP2WPKHOutputScript(feeECKey);
            TransactionOutput txSWFee = new TransactionOutput(params, null, Coin.valueOf(biSWFee.longValue()), outputScript.getProgram());
            outputs.add(txSWFee);

            // add OP_RETURN output
            byte[] idxBuf = ByteBuffer.allocate(4).putInt(feeIdx).array();
            Script op_returnOutputScript = new ScriptBuilder().op(ScriptOpCodes.OP_RETURN).data(idxBuf).build();
            TransactionOutput txFeeIdx = new TransactionOutput(params, null, Coin.valueOf(0L), op_returnOutputScript.getProgram());
            outputs.add(txFeeIdx);

            feeIdx++;   // go to next address index, in prod get index from Samourai API

            //
            //
            //
            // bech32 outputs
            //
            Collections.sort(outputs, new BIP69OutputComparator());
            for(TransactionOutput to : outputs) {
                tx.addOutput(to);
            }

            String utxo = utxos.get(tx0spendFrom);
            String[] s = utxo.split("-");

            Sha256Hash txHash = Sha256Hash.wrap(Hex.decode(s[0]));
            TransactionOutPoint outPoint = new TransactionOutPoint(params, Long.parseLong(s[1]), txHash, Coin.valueOf(biSelectedAmount.longValue()));

            final Script segwitPubkeyScript = ScriptBuilder.createP2WPKHOutputScript(ecKeySpendFrom);
            tx.addSignedInput(outPoint, segwitPubkeyScript, ecKeySpendFrom);

            final String hexTx = new String(Hex.encode(tx.bitcoinSerialize()));
            final String strTxHash = tx.getHashAsString();

            tx.verify();
            //System.out.println(tx);
            System.out.println("tx hash:" + strTxHash);
            System.out.println("tx hex:" + hexTx + "\n");

            for(TransactionOutput to : tx.getOutputs())   {
                toUTXO.put(Hex.toHexString(to.getScriptBytes()), strTxHash + "-" + to.getIndex());
            }

        }
    }

}
