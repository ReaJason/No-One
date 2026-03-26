package com.reajason.noone.core.client;

import lombok.Builder;
import lombok.Data;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Data
@Builder
public class ProxyConfig {
    private String type;
    private String host;
    private int port;
    private String username;
    private String password;

    public Proxy toJavaProxy() {
        Proxy.Type proxyType = "SOCKS5".equalsIgnoreCase(type) || "SOCKS4".equalsIgnoreCase(type)
                ? Proxy.Type.SOCKS
                : Proxy.Type.HTTP;
        return new Proxy(proxyType, new InetSocketAddress(host, port));
    }

    public boolean hasAuth() {
        return username != null && !username.isEmpty();
    }
}
