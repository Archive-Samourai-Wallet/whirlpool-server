package com.samourai.dex.config;

/**
 * This class is exposed by whirlpool-server on <whirlpool-server>/rest/dex-config
 */
public class DexConfigResponse {
    public static final String SIGNING_ADDRESS = "tb1q2qjlvr7gf4km34h2rgkx5wzhceef6ssrmqw65m";

    private String samouraiConfig; // SamouraiConfig serialized as JSON string
    private String signature; // signature of 'samouraiConfig' with SIGNING_ADDRESS

    public DexConfigResponse() {
    }

    // used by whirlpool-server
    public DexConfigResponse(String samouraiConfig, String signature) {
        this.samouraiConfig = samouraiConfig;
        this.signature = signature;
    }

    public String getSamouraiConfig() {
        return samouraiConfig;
    }

    public void setSamouraiConfig(String samouraiConfig) {
        this.samouraiConfig = samouraiConfig;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
