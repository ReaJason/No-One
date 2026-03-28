package com.reajason.noone.core.client.dubbo.alibaba;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.protocol.http.HttpProtocol;
import com.alibaba.dubbo.rpc.protocol.http.HttpRemoteInvocation;
import com.alibaba.dubbo.rpc.service.GenericService;
import com.alibaba.dubbo.rpc.support.ProtocolUtils;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.remoting.httpinvoker.HttpComponentsHttpInvokerRequestExecutor;
import org.springframework.remoting.httpinvoker.HttpInvokerClientConfiguration;
import org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean;
import org.springframework.remoting.httpinvoker.HttpInvokerRequestExecutor;
import org.springframework.remoting.httpinvoker.SimpleHttpInvokerRequestExecutor;
import org.springframework.remoting.support.RemoteInvocation;
import org.springframework.remoting.support.RemoteInvocationFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;

import com.reajason.noone.core.client.dubbo.DubboHttpProxySupport;

/**
 * Alibaba Dubbo SPI {@link com.alibaba.dubbo.rpc.Protocol} that extends {@link HttpProtocol}
 * to use a proxy-aware {@link HttpInvokerRequestExecutor} when proxy URL parameters
 * ({@code proxy.type}, {@code proxy.host}, {@code proxy.port}) are present.
 * <p>
 * When no proxy params exist, delegates to {@link HttpProtocol#doRefer(Class, URL)}.
 * <p>
 * Register via {@code META-INF/dubbo/com.alibaba.dubbo.rpc.Protocol} with key {@code http}.
 */
public class ProxyHttpProtocol extends HttpProtocol {

    private static final String PARAM_PROXY_TYPE = "proxy.type";
    private static final String PARAM_PROXY_HOST = "proxy.host";
    private static final String PARAM_PROXY_PORT = "proxy.port";
    private static final String PARAM_PROXY_USERNAME = "proxy.username";
    private static final String PARAM_PROXY_PASSWORD = "proxy.password";

    private static final String HTTP_CLIENT_SIMPLE = "simple";
    private static final String HTTP_CLIENT_COMMONS = "commons";

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T doRefer(Class<T> serviceType, URL url) throws RpcException {
        String proxyType = url.getParameter(PARAM_PROXY_TYPE);
        if (proxyType == null || proxyType.isEmpty()) {
            return super.doRefer(serviceType, url);
        }

        String generic = url.getParameter(Constants.GENERIC_KEY);
        boolean isGeneric = ProtocolUtils.isGeneric(generic) || serviceType.equals(GenericService.class);

        HttpInvokerProxyFactoryBean httpProxyFactoryBean = new HttpInvokerProxyFactoryBean();
        httpProxyFactoryBean.setRemoteInvocationFactory(new GenericRemoteInvocationFactory(isGeneric, generic));

        String serviceUrl = url.toIdentityString();
        if (isGeneric) {
            serviceUrl = serviceUrl + "/" + Constants.GENERIC_KEY;
        }
        httpProxyFactoryBean.setServiceUrl(serviceUrl);
        httpProxyFactoryBean.setServiceInterface(serviceType);

        String client = url.getParameter(Constants.CLIENT_KEY);
        boolean useSimple = client == null || client.length() == 0 || HTTP_CLIENT_SIMPLE.equals(client);

        String proxyHost = url.getParameter(PARAM_PROXY_HOST);
        int proxyPort = url.getParameter(PARAM_PROXY_PORT, 0);
        String proxyUsername = url.getParameter(PARAM_PROXY_USERNAME);
        String proxyPassword = url.getParameter(PARAM_PROXY_PASSWORD);

        Proxy proxy = DubboHttpProxySupport.createProxy(proxyType, proxyHost, proxyPort);

        if (useSimple) {
            httpProxyFactoryBean.setHttpInvokerRequestExecutor(
                    new ProxySimpleHttpInvokerRequestExecutor(url, proxy, proxyUsername, proxyPassword));
        } else if (HTTP_CLIENT_COMMONS.equals(client)) {
            if (proxy.type() == Proxy.Type.SOCKS) {
                throw new RpcException(
                        "http protocol with proxy.type=SOCKS5 requires client=simple (Apache HttpClient path does not use java.net.Proxy).");
            }
            httpProxyFactoryBean.setHttpInvokerRequestExecutor(
                    newCommonsExecutor(url, proxyHost, proxyPort, proxyUsername, proxyPassword));
        } else {
            throw new IllegalStateException(
                    "Unsupported http protocol client " + client + ", only supported: simple, commons");
        }

        try {
            httpProxyFactoryBean.afterPropertiesSet();
            return (T) httpProxyFactoryBean.getObject();
        } catch (RpcException e) {
            throw e;
        } catch (Exception e) {
            throw new RpcException(e.getMessage(), e);
        }
    }

    private static HttpInvokerRequestExecutor newCommonsExecutor(
            URL url,
            String proxyHost,
            int proxyPort,
            String proxyUsername,
            String proxyPassword) {
        HttpHost apacheProxy = new HttpHost(proxyHost, proxyPort, "http");
        HttpClientBuilder builder = HttpClientBuilder.create().setProxy(apacheProxy);
        if (proxyUsername != null && !proxyUsername.isEmpty()) {
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(proxyHost, proxyPort),
                    new UsernamePasswordCredentials(
                            proxyUsername, proxyPassword != null ? proxyPassword : ""));
            builder.setDefaultCredentialsProvider(credsProvider);
        }
        HttpClient httpClient = builder.build();
        HttpComponentsHttpInvokerRequestExecutor executor =
                new HttpComponentsHttpInvokerRequestExecutor(httpClient);
        executor.setReadTimeout(url.getParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT));
        executor.setConnectTimeout(
                url.getParameter(Constants.CONNECT_TIMEOUT_KEY, Constants.DEFAULT_CONNECT_TIMEOUT));
        return executor;
    }

    private static final class GenericRemoteInvocationFactory implements RemoteInvocationFactory {
        private final boolean generic;
        private final String genericValue;

        private GenericRemoteInvocationFactory(boolean generic, String genericValue) {
            this.generic = generic;
            this.genericValue = genericValue;
        }

        @Override
        public RemoteInvocation createRemoteInvocation(MethodInvocation methodInvocation) {
            HttpRemoteInvocation invocation = new HttpRemoteInvocation(methodInvocation);
            if (generic) {
                invocation.addAttribute(Constants.GENERIC_KEY, genericValue);
            }
            return invocation;
        }
    }

    /**
     * Mirrors {@code HttpProtocol}'s anonymous {@link SimpleHttpInvokerRequestExecutor}:
     * applies Dubbo URL timeouts after {@link #prepareConnection}, and opens the connection via
     * an explicit {@link Proxy} with optional {@code Proxy-Authorization} for HTTP proxies.
     */
    private static final class ProxySimpleHttpInvokerRequestExecutor extends SimpleHttpInvokerRequestExecutor {

        private final URL dubboUrl;
        private final Proxy proxy;
        private final String proxyAuthHeader;
        private final String proxyUsername;
        private final String proxyPassword;

        private ProxySimpleHttpInvokerRequestExecutor(URL dubboUrl, Proxy proxy, String username, String password) {
            this.dubboUrl = dubboUrl;
            this.proxy = proxy;
            this.proxyUsername = username;
            this.proxyPassword = password;
            this.proxyAuthHeader = DubboHttpProxySupport.createProxyAuthorizationHeader(proxy.type(), username, password);
        }

        @Override
        protected HttpURLConnection openConnection(HttpInvokerClientConfiguration config) throws IOException {
            java.net.URL url = new java.net.URL(config.getServiceUrl());
            java.net.URLConnection con;
            try {
                con = DubboHttpProxySupport.withScopedSocksAuthenticator(
                        proxy.type(),
                        proxyUsername,
                        proxyPassword,
                        () -> url.openConnection(proxy));
            } catch (IOException e) {
                throw e;
            } catch (Throwable t) {
                throw new IOException(t);
            }
            if (!(con instanceof HttpURLConnection)) {
                throw new IOException(
                        "Service URL [" + config.getServiceUrl() + "] does not resolve to an HTTP connection");
            }
            return (HttpURLConnection) con;
        }

        @Override
        protected void prepareConnection(HttpURLConnection connection, int contentLength) throws IOException {
            if (proxyAuthHeader != null) {
                connection.setRequestProperty("Proxy-Authorization", proxyAuthHeader);
            }
            super.prepareConnection(connection, contentLength);
            connection.setReadTimeout(dubboUrl.getParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT));
            connection.setConnectTimeout(
                    dubboUrl.getParameter(Constants.CONNECT_TIMEOUT_KEY, Constants.DEFAULT_CONNECT_TIMEOUT));
        }
    }
}
