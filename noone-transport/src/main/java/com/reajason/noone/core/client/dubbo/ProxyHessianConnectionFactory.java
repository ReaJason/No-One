package com.reajason.noone.core.client.dubbo;

import com.caucho.hessian.client.HessianConnection;
import com.caucho.hessian.client.HessianProxyFactory;
import com.caucho.hessian.client.HessianURLConnectionFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

/**
 * {@link HessianURLConnectionFactory} that opens connections through an explicit {@link Proxy}
 * and optionally sets {@code Proxy-Authorization} for authenticated HTTP proxies.
 *
 * <p>Caucho's {@code HessianURLConnection(URL, URLConnection)} constructor is package-private;
 * this class uses reflection to construct it, matching {@link HessianURLConnectionFactory#open(URL)}.
 */
public class ProxyHessianConnectionFactory extends HessianURLConnectionFactory {
    private static final Constructor<?> HESSIAN_URL_CONNECTION_CTOR;
    private static final Field PROXY_FACTORY_FIELD;

    static {
        try {
            Class<?> connClass = Class.forName("com.caucho.hessian.client.HessianURLConnection");
            HESSIAN_URL_CONNECTION_CTOR =
                    connClass.getDeclaredConstructor(URL.class, URLConnection.class);
            HESSIAN_URL_CONNECTION_CTOR.setAccessible(true);
            PROXY_FACTORY_FIELD = HessianURLConnectionFactory.class.getDeclaredField("_proxyFactory");
            PROXY_FACTORY_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Proxy proxy;
    private final String proxyAuthHeader;
    private final String proxyUsername;
    private final String proxyPassword;

    public ProxyHessianConnectionFactory(Proxy proxy, String username, String password) {
        this.proxy = proxy;
        this.proxyUsername = username;
        this.proxyPassword = password;
        this.proxyAuthHeader = DubboHttpProxySupport.createProxyAuthorizationHeader(proxy.type(), username, password);
    }

    @Override
    public HessianConnection open(URL url) throws IOException {
        URLConnection conn;
        try {
            conn = DubboHttpProxySupport.withScopedSocksAuthenticator(
                    proxy.type(),
                    proxyUsername,
                    proxyPassword,
                    () -> url.openConnection(proxy));
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException(t);
        }
        if (proxyAuthHeader != null) {
            conn.setRequestProperty("Proxy-Authorization", proxyAuthHeader);
        }
        HessianProxyFactory proxyFactory = readProxyFactory();
        if (proxyFactory != null) {
            long connectTimeout = proxyFactory.getConnectTimeout();
            if (connectTimeout >= 0) {
                conn.setConnectTimeout((int) connectTimeout);
            }
            long readTimeout = proxyFactory.getReadTimeout();
            if (readTimeout > 0) {
                try {
                    conn.setReadTimeout((int) readTimeout);
                } catch (Throwable ignored) {
                    // Match HessianURLConnectionFactory: ignore read-timeout failures
                }
            }
        }
        conn.setDoOutput(true);
        try {
            return (HessianConnection) HESSIAN_URL_CONNECTION_CTOR.newInstance(url, conn);
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
    }

    private HessianProxyFactory readProxyFactory() throws IOException {
        try {
            return (HessianProxyFactory) PROXY_FACTORY_FIELD.get(this);
        } catch (IllegalAccessException e) {
            throw new IOException(e);
        }
    }
}
