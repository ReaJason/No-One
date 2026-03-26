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

    @Builder.Default
    private int maxRetries = 0;

    @Builder.Default
    private long retryDelayMs = 1000;

    @Builder.Default
    private boolean exponentialBackoff = true;
}
