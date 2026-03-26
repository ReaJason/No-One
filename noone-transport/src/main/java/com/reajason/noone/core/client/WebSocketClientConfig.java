package com.reajason.noone.core.client;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class WebSocketClientConfig {

    private Map<String, String> requestHeaders;

    private ProxyConfig proxy;

    @Builder.Default
    private int connectTimeoutMs = 30000;

    @Builder.Default
    private int readTimeoutMs = 60000;

    @Builder.Default
    private int writeTimeoutMs = 60000;

    @Builder.Default
    private boolean skipSslVerify = false;
}
