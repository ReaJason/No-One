package com.reajason.noone.server.profile.config;

import lombok.Data;

/**
 * Proxy configuration for HTTP/WebSocket clients.
 *
 * @author ReaJason
 */
@Data
public class ProxyConfig {

    private ProxyType type = ProxyType.HTTP;

    private String host;

    private int port;

    private String username;

    private String password;

    public enum ProxyType {
        HTTP,
        SOCKS4,
        SOCKS5
    }
}

