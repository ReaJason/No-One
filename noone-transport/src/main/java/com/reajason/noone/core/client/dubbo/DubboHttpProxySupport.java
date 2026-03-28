package com.reajason.noone.core.client.dubbo;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class DubboHttpProxySupport {

    private static final Object AUTHENTICATOR_LOCK = new Object();
    private static final Field DEFAULT_AUTHENTICATOR_FIELD = resolveDefaultAuthenticatorField();

    private DubboHttpProxySupport() {
    }

    public static Proxy createProxy(String type, String host, int port) {
        Proxy.Type proxyType = "SOCKS5".equalsIgnoreCase(type) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        return new Proxy(proxyType, new InetSocketAddress(host, port));
    }

    public static String createProxyAuthorizationHeader(Proxy.Type proxyType, String username, String password) {
        if (proxyType != Proxy.Type.HTTP || username == null || username.isEmpty()) {
            return null;
        }
        String pwd = password != null ? password : "";
        String encoded = Base64.getEncoder()
                .encodeToString((username + ":" + pwd).getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    public static <T> T withScopedSocksAuthenticator(
            Proxy.Type proxyType,
            String username,
            String password,
            ThrowingSupplier<T> supplier
    ) throws Throwable {
        if (proxyType != Proxy.Type.SOCKS || username == null || username.isEmpty()) {
            return supplier.get();
        }

        synchronized (AUTHENTICATOR_LOCK) {
            Authenticator previous = currentDefaultAuthenticator();
            Authenticator.setDefault(new SocksProxyAuthenticator(username, password));
            try {
                return supplier.get();
            } finally {
                Authenticator.setDefault(previous);
            }
        }
    }

    private static Authenticator currentDefaultAuthenticator() {
        if (DEFAULT_AUTHENTICATOR_FIELD == null) {
            return null;
        }
        try {
            return (Authenticator) DEFAULT_AUTHENTICATOR_FIELD.get(null);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field resolveDefaultAuthenticatorField() {
        try {
            Field field = Authenticator.class.getDeclaredField("theAuthenticator");
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    private static final class SocksProxyAuthenticator extends Authenticator {
        private final PasswordAuthentication authentication;

        private SocksProxyAuthenticator(String username, String password) {
            this.authentication = new PasswordAuthentication(
                    username,
                    (password != null ? password : "").toCharArray());
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            if (getRequestorType() == RequestorType.PROXY) {
                return authentication;
            }
            return null;
        }
    }
}
