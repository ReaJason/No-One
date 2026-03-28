package com.reajason.noone.core.client.dubbo;

import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

import java.net.InetSocketAddress;

public final class DubboProxyUtils {
    private DubboProxyUtils() {}

    public static ProxyHandler createNettyProxyHandler(
            String type, String host, int port, String username, String password) {
        InetSocketAddress proxyAddr = new InetSocketAddress(host, port);
        if ("SOCKS5".equalsIgnoreCase(type)) {
            if (username != null && !username.isEmpty()) {
                return new Socks5ProxyHandler(proxyAddr, username, password);
            }
            return new Socks5ProxyHandler(proxyAddr);
        }
        if (username != null && !username.isEmpty()) {
            return new HttpProxyHandler(proxyAddr, username, password);
        }
        return new HttpProxyHandler(proxyAddr);
    }
}
