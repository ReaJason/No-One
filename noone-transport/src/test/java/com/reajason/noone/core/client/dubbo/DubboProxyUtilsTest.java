package com.reajason.noone.core.client.dubbo;

import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DubboProxyUtilsTest {
    @Test
    void createSocks5HandlerWithoutAuth() {
        var handler = DubboProxyUtils.createNettyProxyHandler("SOCKS5", "127.0.0.1", 1080, null, null);
        assertInstanceOf(Socks5ProxyHandler.class, handler);
        assertEquals(new InetSocketAddress("127.0.0.1", 1080), handler.proxyAddress());
    }

    @Test
    void createSocks5HandlerWithAuth() {
        var handler = DubboProxyUtils.createNettyProxyHandler("SOCKS5", "127.0.0.1", 1080, "user", "pass");
        assertInstanceOf(Socks5ProxyHandler.class, handler);
        Socks5ProxyHandler socks = (Socks5ProxyHandler) handler;
        assertEquals("user", socks.username());
    }

    @Test
    void createHttpHandlerWithoutAuth() {
        var handler = DubboProxyUtils.createNettyProxyHandler("HTTP", "proxy.example.com", 8080, null, null);
        assertInstanceOf(HttpProxyHandler.class, handler);
        assertEquals(new InetSocketAddress("proxy.example.com", 8080), handler.proxyAddress());
    }

    @Test
    void createHttpHandlerWithAuth() {
        var handler = DubboProxyUtils.createNettyProxyHandler("HTTP", "proxy.example.com", 8080, "user", "pass");
        assertInstanceOf(HttpProxyHandler.class, handler);
    }

    @Test
    void typeIsCaseInsensitive() {
        var handler = DubboProxyUtils.createNettyProxyHandler("socks5", "127.0.0.1", 1080, null, null);
        assertInstanceOf(Socks5ProxyHandler.class, handler);
    }

    @Test
    void defaultsToHttpProxy() {
        var handler = DubboProxyUtils.createNettyProxyHandler("UNKNOWN", "127.0.0.1", 8080, null, null);
        assertInstanceOf(HttpProxyHandler.class, handler);
    }
}
