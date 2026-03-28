package com.reajason.noone.core.client.dubbo.apache;

import com.reajason.noone.core.client.dubbo.DubboProxyUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.transport.netty4.NettyClient;
import org.apache.dubbo.remoting.transport.netty4.NettyClientHandler;

/**
 * Extends Dubbo's {@link NettyClient} to prepend a SOCKS5 or HTTP proxy handler
 * as the first handler in the Netty channel pipeline, enabling connections
 * through a proxy server.
 *
 * <p>Proxy configuration is read from the Dubbo {@link URL} parameters:
 * {@code proxy.type}, {@code proxy.host}, {@code proxy.port},
 * {@code proxy.username}, {@code proxy.password}.
 */
public class ProxyNettyClient extends NettyClient {

    private static final String PARAM_PROXY_TYPE = "proxy.type";
    private static final String PARAM_PROXY_HOST = "proxy.host";
    private static final String PARAM_PROXY_PORT = "proxy.port";
    private static final String PARAM_PROXY_USERNAME = "proxy.username";
    private static final String PARAM_PROXY_PASSWORD = "proxy.password";

    public ProxyNettyClient(URL url, org.apache.dubbo.remoting.ChannelHandler handler)
            throws RemotingException {
        super(url, handler);
    }

    @Override
    protected void initBootstrap(NettyClientHandler handler) {
        super.initBootstrap(handler);

        URL url = getUrl();
        String proxyType = url.getParameter(PARAM_PROXY_TYPE);
        if (proxyType == null || proxyType.isEmpty()) {
            return;
        }

        String proxyHost = url.getParameter(PARAM_PROXY_HOST);
        int proxyPort = url.getParameter(PARAM_PROXY_PORT, 0);
        String proxyUsername = url.getParameter(PARAM_PROXY_USERNAME);
        String proxyPassword = url.getParameter(PARAM_PROXY_PASSWORD);

        ChannelHandler originalHandler = getBootstrap().config().handler();
        getBootstrap().handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(originalHandler);
                ch.pipeline().addFirst("proxy",
                        DubboProxyUtils.createNettyProxyHandler(
                                proxyType, proxyHost, proxyPort, proxyUsername, proxyPassword));
            }
        });
    }
}
