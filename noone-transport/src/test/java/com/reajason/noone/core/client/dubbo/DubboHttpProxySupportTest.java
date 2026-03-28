package com.reajason.noone.core.client.dubbo;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DubboHttpProxySupportTest {

    @Test
    void createsBasicHeaderOnlyForHttpProxy() {
        assertEquals(
                "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8)),
                DubboHttpProxySupport.createProxyAuthorizationHeader(Proxy.Type.HTTP, "user", "pass"));
        assertNull(DubboHttpProxySupport.createProxyAuthorizationHeader(Proxy.Type.SOCKS, "user", "pass"));
    }

    @Test
    void scopedSocksAuthenticatorRestoresPreviousDefault() throws Throwable {
        Authenticator original = ReflectionAccess.defaultAuthenticator();
        Authenticator sentinel = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("sentinel", "secret".toCharArray());
            }
        };
        Authenticator.setDefault(sentinel);
        try {
            PasswordAuthentication inside = DubboHttpProxySupport.withScopedSocksAuthenticator(
                    Proxy.Type.SOCKS,
                    "user",
                    "pass",
                    () -> Authenticator.requestPasswordAuthentication(
                            "127.0.0.1",
                            null,
                            1080,
                            "socks5",
                            "prompt",
                            "SOCKS5",
                            null,
                            Authenticator.RequestorType.PROXY));
            assertEquals("user", inside.getUserName());

            PasswordAuthentication after = Authenticator.requestPasswordAuthentication(
                    "127.0.0.1",
                    null,
                    1080,
                    "http",
                    "prompt",
                    "HTTP",
                    null,
                    Authenticator.RequestorType.PROXY);
            assertEquals("sentinel", after.getUserName());
        } finally {
            Authenticator.setDefault(original);
        }
    }

    private static final class ReflectionAccess {
        private static Authenticator defaultAuthenticator() {
            try {
                Field field = Authenticator.class.getDeclaredField("theAuthenticator");
                field.setAccessible(true);
                return (Authenticator) field.get(null);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }
}
