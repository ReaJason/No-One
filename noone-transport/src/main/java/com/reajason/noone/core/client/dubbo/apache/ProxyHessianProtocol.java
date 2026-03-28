package com.reajason.noone.core.client.dubbo.apache;

import com.caucho.hessian.client.HessianConnectionFactory;
import com.caucho.hessian.client.HessianProxyFactory;
import com.reajason.noone.core.client.dubbo.ProxyHessianConnectionFactory;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.protocol.hessian.DubboHessianURLConnectionFactory;
import org.apache.dubbo.rpc.protocol.hessian.HessianProtocol;
import org.apache.dubbo.rpc.protocol.hessian.HttpClientConnectionFactory;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.ProtocolUtils;
import org.apache.dubbo.serialize.hessian.dubbo.Hessian2FactoryInitializer;

import java.net.InetSocketAddress;
import java.net.Proxy;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.remoting.Constants.CLIENT_KEY;
import static org.apache.dubbo.rpc.Constants.GENERIC_KEY;
import static org.apache.dubbo.rpc.protocol.hessian.Constants.DEFAULT_HESSIAN2_REQUEST;
import static org.apache.dubbo.rpc.protocol.hessian.Constants.DEFAULT_HESSIAN_OVERLOAD_METHOD;
import static org.apache.dubbo.rpc.protocol.hessian.Constants.DEFAULT_HTTP_CLIENT;
import static org.apache.dubbo.rpc.protocol.hessian.Constants.HESSIAN2_REQUEST_KEY;
import static org.apache.dubbo.rpc.protocol.hessian.Constants.HESSIAN_OVERLOAD_METHOD_KEY;

/**
 * Dubbo SPI {@link org.apache.dubbo.rpc.Protocol} that extends {@link HessianProtocol}
 * to inject proxy-aware {@link ProxyHessianConnectionFactory} when proxy URL parameters
 * ({@code proxy.type}, {@code proxy.host}, {@code proxy.port}) are present.
 * <p>
 * When no proxy params exist, delegates to default {@link HessianProtocol} behavior.
 * <p>
 * Register via {@code META-INF/dubbo/org.apache.dubbo.rpc.Protocol} with key {@code hessian}.
 */
public class ProxyHessianProtocol extends HessianProtocol {

    private static final String PARAM_PROXY_TYPE = "proxy.type";
    private static final String PARAM_PROXY_HOST = "proxy.host";
    private static final String PARAM_PROXY_PORT = "proxy.port";
    private static final String PARAM_PROXY_USERNAME = "proxy.username";
    private static final String PARAM_PROXY_PASSWORD = "proxy.password";

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T doRefer(Class<T> serviceType, URL url) throws RpcException {
        String proxyType = url.getParameter(PARAM_PROXY_TYPE);
        if (proxyType == null || proxyType.isEmpty()) {
            return super.doRefer(serviceType, url);
        }

        String generic = url.getParameter(GENERIC_KEY);
        boolean isGeneric = ProtocolUtils.isGeneric(generic) || serviceType.equals(GenericService.class);
        if (isGeneric) {
            RpcContext.getContext().setAttachment(GENERIC_KEY, generic);
            url = url.setPath(url.getPath() + "/" + GENERIC_KEY);
        }

        HessianProxyFactory hessianProxyFactory = new HessianProxyFactory();
        boolean isHessian2Request = url.getParameter(HESSIAN2_REQUEST_KEY, DEFAULT_HESSIAN2_REQUEST);
        hessianProxyFactory.setHessian2Request(isHessian2Request);
        boolean isOverloadEnabled = url.getParameter(HESSIAN_OVERLOAD_METHOD_KEY, DEFAULT_HESSIAN_OVERLOAD_METHOD);
        hessianProxyFactory.setOverloadEnabled(isOverloadEnabled);

        String proxyHost = url.getParameter(PARAM_PROXY_HOST);
        int proxyPort = url.getParameter(PARAM_PROXY_PORT, 0);
        String proxyUsername = url.getParameter(PARAM_PROXY_USERNAME);
        String proxyPassword = url.getParameter(PARAM_PROXY_PASSWORD);

        Proxy.Type javaProxyType = "SOCKS5".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        Proxy proxy = new Proxy(javaProxyType, new InetSocketAddress(proxyHost, proxyPort));

        ProxyHessianConnectionFactory factory = new ProxyHessianConnectionFactory(proxy, proxyUsername, proxyPassword);
        factory.setHessianProxyFactory(hessianProxyFactory);
        hessianProxyFactory.setConnectionFactory(factory);

        int timeout = url.getParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT);
        hessianProxyFactory.setConnectTimeout(timeout);
        hessianProxyFactory.setReadTimeout(timeout);
        hessianProxyFactory.setSerializerFactory(Hessian2FactoryInitializer.getInstance().getSerializerFactory());

        return (T) hessianProxyFactory.create(
                serviceType,
                new URL("http", url.getHost(), url.getPort(), url.getPath(), url.getParameters()).toJavaURL(),
                Thread.currentThread().getContextClassLoader());
    }
}
