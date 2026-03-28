package com.reajason.noone.core.client.dubbo;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProxyHessianConnectionFactoryTest {
    @Test
    void constructsWithHttpProxy() {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080));
        ProxyHessianConnectionFactory factory = new ProxyHessianConnectionFactory(proxy, null, null);
        assertNotNull(factory);
    }

    @Test
    void constructsWithSocksProxy() {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1080));
        ProxyHessianConnectionFactory factory = new ProxyHessianConnectionFactory(proxy, null, null);
        assertNotNull(factory);
    }

    @Test
    void constructsWithAuthenticatedProxy() {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080));
        ProxyHessianConnectionFactory factory = new ProxyHessianConnectionFactory(proxy, "user", "pass");
        assertNotNull(factory);
    }

    @Test
    void setsProxyAuthorizationHeaderOnlyForHttpProxy() {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080));
        ProxyHessianConnectionFactory factory = new ProxyHessianConnectionFactory(proxy, "user", "pass");

        assertEquals(
                "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes()),
                readField(factory, "proxyAuthHeader"));
    }

    @Test
    void doesNotSetProxyAuthorizationHeaderForSocksProxy() {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("proxy.example.com", 1080));
        ProxyHessianConnectionFactory factory = new ProxyHessianConnectionFactory(proxy, "user", "pass");

        assertNull(readField(factory, "proxyAuthHeader"));
    }

    private Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
