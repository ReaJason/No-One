package com.reajason.noone.core.client.dubbo.alibaba;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.transport.netty4.NettyClient;
import com.reajason.noone.core.client.dubbo.DubboProxyUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

import java.lang.reflect.Field;

/**
 * Extends Alibaba Dubbo's {@link NettyClient} to prepend a SOCKS5 or HTTP proxy handler
 * as the first handler in the Netty channel pipeline, enabling connections
 * through a proxy server.
 *
 * <p>Proxy configuration is read from the Dubbo {@link URL} parameters:
 * {@code proxy.type}, {@code proxy.host}, {@code proxy.port},
 * {@code proxy.username}, {@code proxy.password}.
 *
 * <p>Unlike Apache Dubbo 3.x which exposes {@code getBootstrap()}, Alibaba Dubbo 2.x
 * keeps the Bootstrap field private, so reflection is used to modify the pipeline
 * after {@link #doOpen()} initializes it.
 */
public class ProxyNettyClient extends NettyClient {

    private static final String PARAM_PROXY_TYPE = "proxy.type";
    private static final String PARAM_PROXY_HOST = "proxy.host";
    private static final String PARAM_PROXY_PORT = "proxy.port";
    private static final String PARAM_PROXY_USERNAME = "proxy.username";
    private static final String PARAM_PROXY_PASSWORD = "proxy.password";

    private static final Field BOOTSTRAP_FIELD;

    static {
        try {
            BOOTSTRAP_FIELD = NettyClient.class.getDeclaredField("bootstrap");
            BOOTSTRAP_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public ProxyNettyClient(URL url, ChannelHandler handler) throws RemotingException {
        super(url, handler);
    }

    @Override
    protected void doOpen() throws Throwable {
        super.doOpen();

        URL url = getUrl();
        String proxyType = url.getParameter(PARAM_PROXY_TYPE);
        if (proxyType == null || proxyType.isEmpty()) {
            return;
        }

        String proxyHost = url.getParameter(PARAM_PROXY_HOST);
        int proxyPort = url.getParameter(PARAM_PROXY_PORT, 0);
        String proxyUsername = url.getParameter(PARAM_PROXY_USERNAME);
        String proxyPassword = url.getParameter(PARAM_PROXY_PASSWORD);

        Bootstrap bootstrap = (Bootstrap) BOOTSTRAP_FIELD.get(this);
        io.netty.channel.ChannelHandler originalHandler = bootstrap.config().handler();
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
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
