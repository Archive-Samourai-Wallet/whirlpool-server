package com.samourai.whirlpool.server.config;

import com.samourai.javaserver.config.ServerConfig;
import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.beans.FailMode;
import com.samourai.whirlpool.server.exceptions.ServerErrorCode;
import com.samourai.whirlpool.server.services.ScodeService;
import com.samourai.whirlpool.server.utils.Utils;
import com.samourai.xmanager.protocol.XManagerService;
import java.lang.invoke.MethodHandles;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "server")
@Configuration
public class WhirlpoolServerConfig extends ServerConfig {

  private SamouraiFeeConfig samouraiFees;
  private MinerFeeConfig minerFees;
  private boolean testMode;
  private FailMode failMode;
  private boolean testnet;
  private boolean mixEnabled;
  private String metricsUrlApp;
  private String metricsUrlSystem;
  private NetworkParameters networkParameters;
  private RpcClientConfig rpcClient;
  private RegisterInputConfig registerInput;
  private RegisterOutputConfig registerOutput;
  private SigningConfig signing;
  private RevealOutputConfig revealOutput;
  private BanConfig ban;
  private ExportConfig export;
  private PartnerConfig[] partners;
  private PoolConfig[] pools;
  private long requestTimeout;
  private SecretWalletConfig signingWallet;

  public SamouraiFeeConfig getSamouraiFees() {
    return samouraiFees;
  }

  public void setSamouraiFees(SamouraiFeeConfig samouraiFees) {
    this.samouraiFees = samouraiFees;
  }

  public MinerFeeConfig getMinerFees() {
    return minerFees;
  }

  public void setMinerFees(MinerFeeConfig minerFees) {
    this.minerFees = minerFees;
  }

  public boolean isTestMode() {
    return testMode;
  }

  public void setTestMode(boolean testMode) {
    this.testMode = testMode;
  }

  public FailMode getFailMode() {
    return failMode;
  }

  public void setFailMode(FailMode failMode) {
    this.failMode = failMode;
  }

  public boolean isMixEnabled() {
    return mixEnabled;
  }

  public void setMixEnabled(boolean mixEnabled) {
    this.mixEnabled = mixEnabled;
  }

  public String getMetricsUrlApp() {
    return metricsUrlApp;
  }

  public void setMetricsUrlApp(String metricsUrlApp) {
    this.metricsUrlApp = metricsUrlApp;
  }

  public String getMetricsUrlSystem() {
    return metricsUrlSystem;
  }

  public void setMetricsUrlSystem(String metricsUrlSystem) {
    this.metricsUrlSystem = metricsUrlSystem;
  }

  public boolean isTestnet() {
    return testnet;
  }

  public void setTestnet(boolean testnet) {
    this.testnet = testnet;
    NetworkParameters networkParameters = testnet ? TestNet3Params.get() : MainNetParams.get();
    this.networkParameters = networkParameters;
  }

  public NetworkParameters getNetworkParameters() {
    return networkParameters;
  }

  public RpcClientConfig getRpcClient() {
    return rpcClient;
  }

  public void setRpcClient(RpcClientConfig rpcClient) {
    this.rpcClient = rpcClient;
  }

  public RegisterInputConfig getRegisterInput() {
    return registerInput;
  }

  public void setRegisterInput(RegisterInputConfig registerInput) {
    this.registerInput = registerInput;
  }

  public RegisterOutputConfig getRegisterOutput() {
    return registerOutput;
  }

  public void setRegisterOutput(RegisterOutputConfig registerOutput) {
    this.registerOutput = registerOutput;
  }

  public SigningConfig getSigning() {
    return signing;
  }

  public void setSigning(SigningConfig signing) {
    this.signing = signing;
  }

  public RevealOutputConfig getRevealOutput() {
    return revealOutput;
  }

  public void setRevealOutput(RevealOutputConfig revealOutput) {
    this.revealOutput = revealOutput;
  }

  public BanConfig getBan() {
    return ban;
  }

  public void setBan(BanConfig ban) {
    this.ban = ban;
  }

  public ExportConfig getExport() {
    return export;
  }

  public void setExport(ExportConfig export) {
    this.export = export;
  }

  public PartnerConfig[] getPartners() {
    return partners;
  }

  public void setPartners(PartnerConfig[] partners) {
    this.partners = partners;
  }

  public PoolConfig[] getPools() {
    return pools;
  }

  public void setPools(PoolConfig[] pools) {
    this.pools = pools;
  }

  public long getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(long requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public SecretWalletConfig getSigningWallet() {
    return signingWallet;
  }

  public void setSigningWallet(SecretWalletConfig signingWallet) {
    this.signingWallet = signingWallet;
  }

  public static class RegisterInputConfig {
    private int minConfirmationsMustMix;
    private int minConfirmationsLiquidity;
    private int maxInputsSameHash;
    private int maxInputsSameUserHash;
    private long confirmInterval;

    public int getMinConfirmationsMustMix() {
      return minConfirmationsMustMix;
    }

    public void setMinConfirmationsMustMix(int minConfirmationsMustMix) {
      this.minConfirmationsMustMix = minConfirmationsMustMix;
    }

    public int getMinConfirmationsLiquidity() {
      return minConfirmationsLiquidity;
    }

    public void setMinConfirmationsLiquidity(int minConfirmationsLiquidity) {
      this.minConfirmationsLiquidity = minConfirmationsLiquidity;
    }

    public int getMaxInputsSameHash() {
      return maxInputsSameHash;
    }

    public void setMaxInputsSameHash(int maxInputsSameHash) {
      this.maxInputsSameHash = maxInputsSameHash;
    }

    public int getMaxInputsSameUserHash() {
      return maxInputsSameUserHash;
    }

    public void setMaxInputsSameUserHash(int maxInputsSameUserHash) {
      this.maxInputsSameUserHash = maxInputsSameUserHash;
    }

    public long getConfirmInterval() {
      return confirmInterval;
    }

    public void setConfirmInterval(long confirmInterval) {
      this.confirmInterval = confirmInterval;
    }
  }

  public static class RegisterOutputConfig {
    private int timeout;

    public int getTimeout() {
      return timeout;
    }

    public void setTimeout(int timeout) {
      this.timeout = timeout;
    }
  }

  public static class SigningConfig {
    private int timeout;

    public int getTimeout() {
      return timeout;
    }

    public void setTimeout(int timeout) {
      this.timeout = timeout;
    }
  }

  public static class RevealOutputConfig {
    private int timeout;

    public int getTimeout() {
      return timeout;
    }

    public void setTimeout(int timeout) {
      this.timeout = timeout;
    }
  }

  public static class BanConfig {
    private int blames;
    private long period;
    private long expiration;
    private int recidivismFactor;

    public int getBlames() {
      return blames;
    }

    public void setBlames(int blames) {
      this.blames = blames;
    }

    public long getPeriod() {
      return period;
    }

    public void setPeriod(long period) {
      this.period = period;
    }

    public long getExpiration() {
      return expiration;
    }

    public void setExpiration(long expiration) {
      this.expiration = expiration;
    }

    public int getRecidivismFactor() {
      return recidivismFactor;
    }

    public void setRecidivismFactor(int recidivismFactor) {
      this.recidivismFactor = recidivismFactor;
    }
  }

  public static class ExportConfig {
    private ExportItemConfig mixs;
    private ExportItemConfig activity;

    public ExportItemConfig getMixs() {
      return mixs;
    }

    public void setMixs(ExportItemConfig mixs) {
      this.mixs = mixs;
    }

    public ExportItemConfig getActivity() {
      return activity;
    }

    public void setActivity(ExportItemConfig activity) {
      this.activity = activity;
    }
  }

  public static class ExportItemConfig {
    private String filename;
    private String directory;

    public String getFilename() {
      return filename;
    }

    public void setFilename(String filename) {
      this.filename = filename;
    }

    public String getDirectory() {
      return directory;
    }

    public void setDirectory(String directory) {
      this.directory = directory;
    }
  }

  public static class PartnerConfig {
    @NotEmpty private String id;
    @NotEmpty private short payload;
    @NotEmpty private String xmService;

    public void validate() throws Exception {
      if (StringUtils.isEmpty(id)) {
        throw new Exception("Invalid partner.id");
      }
      if (payload < 0) {
        throw new Exception("Invalid partner.payload");
      }
      if (XManagerService.valueOf(xmService) == null) {
        throw new Exception("Invalid partner.xmService");
      }
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public short getPayload() {
      return payload;
    }

    public void setPayload(short payload) {
      this.payload = payload;
    }

    public String getXmService() {
      return xmService;
    }

    public void setXmService(String xmService) {
      this.xmService = xmService;
    }

    @Override
    public String toString() {
      return "id=" + id + ", payload=" + payload + ", xmService=" + xmService;
    }
  }

  public static class PoolConfig {
    private String id;
    private long denomination;
    private long feeValue;
    private Map<Long, Long> feeAccept;
    private MinerFeeConfig minerFees;
    private int mustMixMin;
    private int liquidityMin;
    private int surge;
    private int anonymitySet;
    private int tx0MaxOutputs;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public long getDenomination() {
      return denomination;
    }

    public void setDenomination(long denomination) {
      this.denomination = denomination;
    }

    public long getFeeValue() {
      return feeValue;
    }

    public void setFeeValue(long feeValue) {
      this.feeValue = feeValue;
    }

    public Map<Long, Long> getFeeAccept() {
      return feeAccept;
    }

    public void setFeeAccept(Map<Long, Long> feeAccept) {
      this.feeAccept = feeAccept;
    }

    public MinerFeeConfig getMinerFees() {
      return minerFees;
    }

    public void setMinerFees(MinerFeeConfig minerFees) {
      this.minerFees = minerFees;
    }

    public int getMustMixMin() {
      return mustMixMin;
    }

    public void setMustMixMin(int mustMixMin) {
      this.mustMixMin = mustMixMin;
    }

    public int getLiquidityMin() {
      return liquidityMin;
    }

    public void setLiquidityMin(int liquidityMin) {
      this.liquidityMin = liquidityMin;
    }

    public int getSurge() {
      return surge;
    }

    public void setSurge(int surge) {
      this.surge = surge;
    }

    public int getAnonymitySet() {
      return anonymitySet;
    }

    public void setAnonymitySet(int anonymitySet) {
      this.anonymitySet = anonymitySet;
    }

    public int getTx0MaxOutputs() {
      return tx0MaxOutputs;
    }

    public void setTx0MaxOutputs(int tx0MaxOutputs) {
      this.tx0MaxOutputs = tx0MaxOutputs;
    }

    public String toString() {
      String poolInfo = "denomination=" + Utils.satoshisToBtc(denomination);
      poolInfo +=
          ", feeValue="
              + Utils.satoshisToBtc(feeValue)
              + ", feeAccept="
              + (feeAccept != null ? feeAccept : null)
              + ", anonymitySet="
              + anonymitySet;
      poolInfo += ", minerFees=" + (minerFees != null ? minerFees.toString() : "null");
      poolInfo +=
          ", mustMixMin="
              + getMustMixMin()
              + ", liquidityMin="
              + getLiquidityMin()
              + ", surge="
              + getSurge()
              + ", tx0MaxOutputs="
              + tx0MaxOutputs;
      return poolInfo;
    }
  }

  public static class RpcClientConfig {
    @NotEmpty private String protocol;
    @NotEmpty private String host;
    @NotEmpty private int port;
    @NotEmpty private String user;
    private String password;
    private boolean mockTxBroadcast;
    private int blockHeightMaxSpread;

    public String getProtocol() {
      return protocol;
    }

    public void setProtocol(String protocol) {
      this.protocol = protocol;
    }

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public String getUser() {
      return user;
    }

    public void setUser(String user) {
      this.user = user;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public boolean isMockTxBroadcast() {
      return mockTxBroadcast;
    }

    public void setMockTxBroadcast(boolean mockTxBroadcast) {
      this.mockTxBroadcast = mockTxBroadcast;
    }

    public int getBlockHeightMaxSpread() {
      return blockHeightMaxSpread;
    }

    public void setBlockHeightMaxSpread(int blockHeightMaxSpread) {
      this.blockHeightMaxSpread = blockHeightMaxSpread;
    }
  }

  public static class SamouraiFeeConfig {
    private SecretWalletConfig secretWalletV0; // for FeeOpReturnImplV0
    private SecretWalletConfig secretWallet; // for >= FeeOpReturnImplV1
    private Map<String, ScodeSamouraiFeeConfig> scodes = new HashMap<>(); // -32,768 to 32,767
    private Map<String, ScodeSamouraiFeeConfig> scodesUpperCase;

    public void validate() throws Exception {
      for (Map.Entry<String, ScodeSamouraiFeeConfig> scodeEntry : scodes.entrySet()) {
        if (StringUtils.isEmpty(scodeEntry.getKey())) {
          throw new Exception("Invalid scode ID: empty");
        }
        scodeEntry.getValue().validate();
      }
    }

    public SecretWalletConfig getSecretWalletV0() {
      return secretWalletV0;
    }

    public void setSecretWalletV0(SecretWalletConfig secretWalletV0) {
      this.secretWalletV0 = secretWalletV0;
    }

    public SecretWalletConfig getSecretWallet() {
      return secretWallet;
    }

    public void setSecretWallet(SecretWalletConfig secretWallet) {
      this.secretWallet = secretWallet;
    }

    public Map<String, ScodeSamouraiFeeConfig> getScodes() {
      if (scodesUpperCase == null) {
        scodesUpperCase = new HashMap<>();
        for (Map.Entry<String, ScodeSamouraiFeeConfig> e : scodes.entrySet()) {
          scodesUpperCase.put(e.getKey().toUpperCase(), e.getValue());
        }
      }
      return scodesUpperCase;
    }

    public void setScodes(Map<String, ScodeSamouraiFeeConfig> scodes) {
      this.scodes = scodes;
      this.scodesUpperCase = null;
    }
  }

  public static class MinerFeeConfig {
    private long minerFeeMin; // in satoshis
    private long minerFeeCap; // in satoshis
    private long minerFeeMax; // in satoshis
    private long minRelayFee; // in satoshis
    private long surgeRelayFee; // in satoshis

    public void validate() throws Exception {
      if (minerFeeMin <= 0) {
        throw new Exception("Invalid minerFeeMin");
      }
      if (minerFeeCap <= 0) {
        throw new Exception("Invalid minerFeeCap");
      }
      if (minerFeeMax <= 0) {
        throw new Exception("Invalid minerFeeMax");
      }
      if (minRelayFee <= 0) {
        throw new Exception("Invalid minRelayFee");
      }
      if (surgeRelayFee <= 0) {
        throw new Exception("Invalid minRelayFee");
      }
    }

    public long getMinerFeeMin() {
      return minerFeeMin;
    }

    public void setMinerFeeMin(long minerFeeMin) {
      this.minerFeeMin = minerFeeMin;
    }

    public long getMinerFeeCap() {
      return minerFeeCap;
    }

    public void setMinerFeeCap(long minerFeeCap) {
      this.minerFeeCap = minerFeeCap;
    }

    public long getMinerFeeMax() {
      return minerFeeMax;
    }

    public void setMinerFeeMax(long minerFeeMax) {
      this.minerFeeMax = minerFeeMax;
    }

    public long getMinRelayFee() {
      return minRelayFee;
    }

    public void setMinRelayFee(long minRelayFee) {
      this.minRelayFee = minRelayFee;
    }

    public long getSurgeRelayFee() {
      return surgeRelayFee;
    }

    public void setSurgeRelayFee(long surgeRelayFee) {
      this.surgeRelayFee = surgeRelayFee;
    }

    @Override
    public String toString() {
      return "["
          + minerFeeMin
          + "-"
          + minerFeeCap
          + ", max="
          + minerFeeMax
          + "], minRelayFee="
          + minRelayFee
          + ", surgeRelayFee="
          + surgeRelayFee;
    }
  }

  public static class ScodeSamouraiFeeConfig {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @NotEmpty private Short payload;
    @NotEmpty private Integer feeValuePercent; // 0-100
    private Long expiration;
    private String message;

    public void validate() throws Exception {
      if (payload == null || payload == 0) {
        throw new Exception("Invalid scode.payload");
      }
      if (payload.equals(ScodeService.SCODE_CASCADING_PAYLOAD)) {
        throw new Exception("Invalid scode.payload: value is reserved for CASCADING");
      }
      if (feeValuePercent == null || feeValuePercent < 0 || feeValuePercent > 99) {
        throw new Exception("Invalid scode.feeValuePercent");
      }
      if (expiration != null && expiration <= 0) {
        throw new Exception("Invalid scode.expiration");
      }
    }

    public boolean isValidAt(long tx0Time) {
      // check expiration
      if (expiration != null) {
        if (tx0Time > expiration) {
          return false;
        }
      }
      return true;
    }

    public boolean isCascading() {
      return payload.equals(ScodeService.SCODE_CASCADING_PAYLOAD);
    }

    public Short getPayload() {
      return payload;
    }

    public void setPayload(Short payload) {
      this.payload = payload;
    }

    public Integer getFeeValuePercent() {
      return feeValuePercent;
    }

    public void setFeeValuePercent(Integer feeValuePercent) {
      this.feeValuePercent = feeValuePercent;
    }

    public Long getExpiration() {
      return expiration;
    }

    public void setExpiration(Long expiration) {
      this.expiration = expiration;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }
  }

  public static class SecretWalletConfig {
    @NotEmpty private String words;
    @NotEmpty private String passphrase;

    public String getWords() {
      return words;
    }

    public void setWords(String words) {
      this.words = words;
    }

    public String getPassphrase() {
      return passphrase;
    }

    public void setPassphrase(String passphrase) {
      this.passphrase = passphrase;
    }
  }

  @Override
  public void validate() throws Exception {
    super.validate();
    samouraiFees.validate();
    minerFees.validate();
    for (PartnerConfig partnerConfig : partners) {
      partnerConfig.validate();
    }
  }

  @Override
  public Map<String, String> getConfigInfo() {
    Map<String, String> configInfo = super.getConfigInfo();
    configInfo.put("testMode", String.valueOf(testMode));
    configInfo.put("failMode", String.valueOf(failMode));
    configInfo.put(
        "rpcClient",
        rpcClient.getHost() + ":" + rpcClient.getPort() + "," + networkParameters.getId());
    configInfo.put("protocolVersion", WhirlpoolProtocol.PROTOCOL_VERSION);

    int nbSeedWordsV0 = samouraiFees.getSecretWalletV0().getWords().split(" ").length;
    int nbSeedWords = samouraiFees.getSecretWallet().getWords().split(" ").length;
    configInfo.put(
        "samouraiFees",
        "secretWalletV0=(" + nbSeedWordsV0 + " words), secretWallet=(" + nbSeedWords + " words)");
    configInfo.put("minerFees", minerFees.toString());

    configInfo.put(
        "registerInput.maxInputsSameHash", String.valueOf(registerInput.maxInputsSameHash));
    configInfo.put(
        "registerInput.maxInputsSameUserHash", String.valueOf(registerInput.maxInputsSameUserHash));
    configInfo.put(
        "registerInput.minConfirmations",
        "liquidity="
            + registerInput.minConfirmationsLiquidity
            + ", mustMix="
            + registerInput.minConfirmationsMustMix);
    configInfo.put("registerInput.confirmInterval", String.valueOf(registerInput.confirmInterval));

    String timeoutInfo =
        "registerOutput="
            + String.valueOf(registerOutput.timeout)
            + ", signing="
            + String.valueOf(signing.timeout)
            + ", revealOutput="
            + String.valueOf(revealOutput.timeout);
    configInfo.put("timeouts", timeoutInfo);
    configInfo.put("export.mixs", export.mixs.directory + " -> " + export.mixs.filename);
    configInfo.put(
        "export.activity", export.activity.directory + " -> " + export.activity.filename);
    configInfo.put(
        "ban",
        "blames="
            + String.valueOf(ban.blames)
            + ", period="
            + ban.period
            + ", expiration="
            + ban.expiration
            + ", recidivismFactor="
            + ban.recidivismFactor);
    for (PartnerConfig partnerConfig : partners) {
      configInfo.put("partners[" + partnerConfig.id + "]", partnerConfig.toString());
    }
    for (PoolConfig poolConfig : pools) {
      configInfo.put("pools[" + poolConfig.id + "]", poolConfig.toString());
    }
    long now = System.currentTimeMillis();
    int i = 0;
    for (Map.Entry<String, ScodeSamouraiFeeConfig> feePayloadEntry :
        samouraiFees.getScodes().entrySet()) {
      String scode = Utils.obfuscateString(feePayloadEntry.getKey(), 1);
      ScodeSamouraiFeeConfig scodeConfig = feePayloadEntry.getValue();
      String scodeInfo =
          "scode=" + scode + ", feeValuePercent=" + scodeConfig.feeValuePercent + "%";
      if (scodeConfig.expiration != null) {
        boolean stillValid = scodeConfig.isValidAt(now);
        scodeInfo +=
            ", expiration="
                + new Date(scodeConfig.expiration).toString()
                + ", stillValid="
                + stillValid;
      }
      configInfo.put("scode[" + i + "]", scodeInfo);
      i++;
    }
    return configInfo;
  }

  public void checkFailMode(FailMode value) throws NotifiableException {
    // fail mode?
    if (failMode.equals(value)) {
      throw new NotifiableException(
          ServerErrorCode.INPUT_REJECTED, "serverConfig.failMode=" + failMode);
    }
  }
}
