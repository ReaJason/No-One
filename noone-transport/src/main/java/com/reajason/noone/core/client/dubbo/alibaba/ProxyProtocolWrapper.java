package com.reajason.noone.core.client.dubbo.alibaba;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.protocol.AbstractProxyProtocol;

/**
 * Dubbo 2.x Protocol wrapper that intercepts {@code hessian://} and {@code http://}
 * protocol references to inject proxy support.
 *
 * <p>In Dubbo 2.x, SPI extensions with the same key as internal extensions are rejected
 * ("Duplicate extension"). This wrapper avoids that by decorating ALL protocols transparently.
 * For non-proxied calls or non-HTTP protocols, it delegates directly to the wrapped protocol.
 *
 * <p>Dubbo recognizes this class as a wrapper because it has a constructor taking {@link Protocol}.
 */
public class ProxyProtocolWrapper implements Protocol {

    private final Protocol protocol;

    public ProxyProtocolWrapper(Protocol protocol) {
        this.protocol = protocol;
    }

    @Override
    public int getDefaultPort() {
        return protocol.getDefaultPort();
    }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        return protocol.export(invoker);
    }

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        String proxyType = url.getParameter("proxy.type");
        if (proxyType == null || proxyType.isEmpty()) {
            return protocol.refer(type, url);
        }

        String urlProtocol = url.getProtocol();
        if ("hessian".equals(urlProtocol)) {
            return createAndRefer(new ProxyHessianProtocol(), type, url);
        }
        if ("http".equals(urlProtocol)) {
            return createAndRefer(new ProxyHttpProtocol(), type, url);
        }

        return protocol.refer(type, url);
    }

    private <T> Invoker<T> createAndRefer(AbstractProxyProtocol proxyProtocol, Class<T> type, URL url) {
        ProxyFactory proxyFactory = ExtensionLoader
                .getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
        proxyProtocol.setProxyFactory(proxyFactory);
        return proxyProtocol.refer(type, url);
    }

    @Override
    public void destroy() {
        protocol.destroy();
    }
}
