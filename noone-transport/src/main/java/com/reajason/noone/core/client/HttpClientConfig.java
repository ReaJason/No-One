package com.reajason.noone.core.client;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class HttpClientConfig {

    private String requestMethod;
    private Map<String, String> requestHeaders;
    private Map<String, String> requestParams;
    private Map<String, String> requestCookies;

    @Builder.Default
    private String contentType = "application/octet-stream";

    private Integer expectedResponseStatusCode;

    private ProxyConfig proxy;

    @Builder.Default
    private int connectTimeoutMs = 30000;

    @Builder.Default
    private int readTimeoutMs = 60000;

    @Builder.Default
    private int writeTimeoutMs = 60000;

    @Builder.Default
    private boolean skipSslVerify = false;

    /**
     * Switches TLS/JA3/JA4 and HTTP/2 fingerprint impersonation on. Off by default so existing
     * connections keep their current behaviour.
     */
    @Builder.Default
    private boolean browserImpersonation = false;

    /**
     * Browser to impersonate when {@link #browserImpersonation} is on. Defaults to
     * {@link BrowserImpersonationProfile#DEFAULT} when left empty.
     */
    private BrowserImpersonationProfile impersonationProfile;

    /**
     * Base64 encoded PKCS#12 keystore holding the client certificate and private key for mutual TLS.
     */
    private String clientCertificate;

    /**
     * Password of {@link #clientCertificate}. {@code null} or empty for an unprotected keystore.
     */
    private String clientCertificatePassword;

    @Builder.Default
    private int maxRetries = 0;

    @Builder.Default
    private long retryDelayMs = 1000;

    @Builder.Default
    private boolean exponentialBackoff = true;

    /**
     * @return the profile to impersonate, or {@code null} when impersonation is switched off
     */
    public BrowserImpersonationProfile resolveImpersonationProfile() {
        return BrowserImpersonationProfile.resolve(browserImpersonation, impersonationProfile);
    }
}
