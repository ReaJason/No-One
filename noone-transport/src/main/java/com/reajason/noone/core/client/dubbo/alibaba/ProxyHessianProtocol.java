package com.reajason.noone.core.client.dubbo.alibaba;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.protocol.hessian.HessianProtocol;
import com.alibaba.dubbo.rpc.protocol.hessian.serialization.Hessian2FactoryUtil;
import com.alibaba.dubbo.rpc.service.GenericService;
import com.alibaba.dubbo.rpc.support.ProtocolUtils;
import com.caucho.hessian.client.HessianProxyFactory;
import com.reajason.noone.core.client.dubbo.ProxyHessianConnectionFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * Alibaba Dubbo SPI {@link com.alibaba.dubbo.rpc.Protocol} that extends {@link HessianProtocol}
 * to inject proxy-aware {@link ProxyHessianConnectionFactory} when proxy URL parameters
 * ({@code proxy.type}, {@code proxy.host}, {@code proxy.port}) are present.
 * <p>
 * When no proxy params exist, delegates to default {@link HessianProtocol} behavior.
 * <p>
 * Register via {@code META-INF/dubbo/com.alibaba.dubbo.rpc.Protocol} with key {@code hessian}.
 *
 * <p>Note: Apache Dubbo 3.x's backward-compatibility shim redefines
 * {@code com.alibaba.dubbo.common.Constants} without Hessian-specific fields
 * ({@code HESSIAN2_REQUEST_KEY}, {@code HESSIAN_OVERLOAD_METHOD_KEY}), so those
 * values are inlined here.
 */
public class ProxyHessianProtocol extends HessianProtocol {

    private static final String PARAM_PROXY_TYPE = "proxy.type";
    private static final String PARAM_PROXY_HOST = "proxy.host";
    private static final String PARAM_PROXY_PORT = "proxy.port";
    private static final String PARAM_PROXY_USERNAME = "proxy.username";
    private static final String PARAM_PROXY_PASSWORD = "proxy.password";

    private static final String HESSIAN2_REQUEST_KEY = "hessian2.request";
    private static final boolean DEFAULT_HESSIAN2_REQUEST = false;
    private static final String HESSIAN_OVERLOAD_METHOD_KEY = "hessian.overload.method";
    private static final boolean DEFAULT_HESSIAN_OVERLOAD_METHOD = false;

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T doRefer(Class<T> serviceType, URL url) throws RpcException {
        String proxyType = url.getParameter(PARAM_PROXY_TYPE);
        if (proxyType == null || proxyType.isEmpty()) {
            return super.doRefer(serviceType, url);
        }

        String generic = url.getParameter(Constants.GENERIC_KEY);
        boolean isGeneric = ProtocolUtils.isGeneric(generic) || serviceType.equals(GenericService.class);
        if (isGeneric) {
            RpcContext.getContext().setAttachment(Constants.GENERIC_KEY, generic);
            url = url.setPath(url.getPath() + "/" + Constants.GENERIC_KEY);
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

        int timeout = url.getParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
        hessianProxyFactory.setConnectTimeout(timeout);
        hessianProxyFactory.setReadTimeout(timeout);
        hessianProxyFactory.setSerializerFactory(Hessian2FactoryUtil.getInstance().getSerializerFactory());

        return (T) hessianProxyFactory.create(
                serviceType,
                url.setProtocol("http").toJavaURL(),
                Thread.currentThread().getContextClassLoader());
    }
}
