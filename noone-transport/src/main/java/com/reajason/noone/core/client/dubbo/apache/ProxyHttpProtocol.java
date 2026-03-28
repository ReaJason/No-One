package com.reajason.noone.core.client.dubbo.apache;

import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import com.googlecode.jsonrpc4j.spring.JsonProxyFactoryBean;
import com.reajason.noone.core.client.dubbo.DubboHttpProxySupport;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.protocol.AbstractProxyProtocol;
import org.apache.dubbo.rpc.service.GenericException;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.remoting.Constants.CONNECT_TIMEOUT_KEY;
import static org.apache.dubbo.remoting.Constants.DEFAULT_CONNECT_TIMEOUT;
import static org.apache.dubbo.rpc.Constants.GENERIC_KEY;

/**
 * Apache Dubbo SPI Protocol for {@code http://} URLs using JSON-RPC (jsonrpc4j).
 * Compatible with Apache Dubbo 2.7.x and 3.x HTTP protocol servers.
 * Injects proxy support via {@link JsonRpcHttpClient#setConnectionProxy(Proxy)}.
 */
public class ProxyHttpProtocol extends AbstractProxyProtocol {

    private static final String PARAM_PROXY_TYPE = "proxy.type";
    private static final String PARAM_PROXY_HOST = "proxy.host";
    private static final String PARAM_PROXY_PORT = "proxy.port";

    @Override
    public int getDefaultPort() {
        return 80;
    }

    @Override
    protected <T> Runnable doExport(T impl, Class<T> type, URL url) throws RpcException {
        throw new RpcException(RpcException.UNKNOWN_EXCEPTION,
                "HTTP protocol export is not supported by ProxyHttpProtocol. This is a client-only protocol.");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T doRefer(Class<T> serviceType, URL url) throws RpcException {
        String generic = url.getParameter(GENERIC_KEY);
        boolean isGeneric = ProtocolUtils.isGeneric(generic) || serviceType.equals(GenericService.class);
        String serviceUrl = resolveServiceUrl(url);

        if (isGeneric) {
            return (T) new JsonRpcGenericService(buildJsonRpcHttpClient(serviceUrl, url));
        }

        JsonProxyFactoryBean jsonProxyFactoryBean = new JsonProxyFactoryBean();
        jsonProxyFactoryBean.setServiceUrl(serviceUrl);
        jsonProxyFactoryBean.setServiceInterface(serviceType);
        jsonProxyFactoryBean.setJsonRpcHttpClient(buildJsonRpcHttpClient(serviceUrl, url));
        jsonProxyFactoryBean.afterPropertiesSet();

        return (T) jsonProxyFactoryBean.getObject();
    }

    private static String resolveServiceUrl(URL url) {
        try {
            String path = url.getPath();
            if (path == null || path.isEmpty()) {
                path = "/" + url.getServiceInterface();
            } else if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return new java.net.URL("http", url.getHost(), url.getPort(), path).toString();
        } catch (java.net.MalformedURLException e) {
            throw new RpcException("Invalid service URL: " + url, e);
        }
    }

    static JsonRpcHttpClient buildJsonRpcHttpClient(String serviceUrl, URL url) {
        String proxyType = url.getParameter(PARAM_PROXY_TYPE);
        Map<String, String> headers = new HashMap<>();
        Proxy proxy = Proxy.NO_PROXY;
        String proxyUsername = null;
        String proxyPassword = null;

        if (proxyType != null && !proxyType.isEmpty()) {
            String proxyHost = url.getParameter(PARAM_PROXY_HOST);
            int proxyPort = url.getParameter(PARAM_PROXY_PORT, 0);
            proxyUsername = url.getParameter("proxy.username");
            proxyPassword = url.getParameter("proxy.password");
            proxy = DubboHttpProxySupport.createProxy(proxyType, proxyHost, proxyPort);
            String proxyAuthorization = DubboHttpProxySupport.createProxyAuthorizationHeader(
                    proxy.type(), proxyUsername, proxyPassword);
            if (proxyAuthorization != null) {
                headers.put("Proxy-Authorization", proxyAuthorization);
            }
        }

        try {
            JsonRpcHttpClient httpClient = new ProxyAwareJsonRpcHttpClient(
                    serviceUrl,
                    headers,
                    proxy,
                    proxyUsername,
                    proxyPassword);
            httpClient.setConnectionProxy(proxy);
            httpClient.setReadTimeoutMillis(url.getParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT));
            httpClient.setConnectionTimeoutMillis(url.getParameter(CONNECT_TIMEOUT_KEY, DEFAULT_CONNECT_TIMEOUT));
            return httpClient;
        } catch (MalformedURLException e) {
            throw new RpcException("Invalid service URL: " + serviceUrl, e);
        }
    }

    private static final class ProxyAwareJsonRpcHttpClient extends JsonRpcHttpClient {
        private final Proxy proxy;
        private final String proxyUsername;
        private final String proxyPassword;

        private ProxyAwareJsonRpcHttpClient(
                String serviceUrl,
                Map<String, String> headers,
                Proxy proxy,
                String proxyUsername,
                String proxyPassword
        ) throws MalformedURLException {
            super(new java.net.URL(serviceUrl), headers);
            this.proxy = proxy;
            this.proxyUsername = proxyUsername;
            this.proxyPassword = proxyPassword;
        }

        @Override
        public Object invoke(String methodName, Object argument, Type returnType, Map<String, String> extraHeaders)
                throws Throwable {
            return DubboHttpProxySupport.withScopedSocksAuthenticator(
                    proxy.type(),
                    proxyUsername,
                    proxyPassword,
                    () -> super.invoke(methodName, argument, returnType, extraHeaders));
        }
    }

    private static final class JsonRpcGenericService implements GenericService {
        private final JsonRpcHttpClient httpClient;

        private JsonRpcGenericService(JsonRpcHttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public Object $invoke(String method, String[] parameterTypes, Object[] args) throws GenericException {
            try {
                return httpClient.invoke(method, args, inferReturnType(parameterTypes));
            } catch (Throwable e) {
                if (e instanceof Error) {
                    throw (Error) e;
                }
                throw new GenericException(
                        "HTTP JSON-RPC generic invocation failed: " + method,
                        e,
                        e.getClass().getName(),
                        e.getMessage());
            }
        }

        private Type inferReturnType(String[] parameterTypes) {
            if (parameterTypes != null && parameterTypes.length > 0 && isByteArrayType(parameterTypes[0])) {
                return byte[].class;
            }
            return Object.class;
        }

        private boolean isByteArrayType(String parameterType) {
            return byte[].class.getName().equals(parameterType) || "byte[]".equals(parameterType);
        }
    }
}
