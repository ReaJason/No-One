package com.reajason.noone.core.client.dubbo.apache;

import com.reajason.noone.core.client.dubbo.DubboProxyUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.api.WireProtocol;
import org.apache.dubbo.remoting.api.pu.ChannelOperator;
import org.apache.dubbo.remoting.transport.netty4.AbstractNettyConnectionClient;
import org.apache.dubbo.remoting.transport.netty4.NettyConnectionHandler;
import org.apache.dubbo.remoting.transport.netty4.NettyEventLoopFactory;
import org.apache.dubbo.remoting.transport.netty4.NettySslContextOperator;
import org.apache.dubbo.remoting.transport.netty4.http2.Http2ClientSettingsHandler;
import org.apache.dubbo.remoting.transport.netty4.ssl.SslClientTlsHandler;
import org.apache.dubbo.remoting.transport.netty4.ssl.SslContexts;
import org.apache.dubbo.remoting.utils.UrlUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.dubbo.remoting.transport.netty4.NettyEventLoopFactory.socketChannelClass;

/**
 * Mirrors {@code NettyConnectionClient} but injects a SOCKS5 or HTTP proxy handler
 * as the first handler in the Netty pipeline. Required for tri:// protocol proxy
 * support because {@code NettyConnectionClient} is {@code final}.
 *
 * <p>Proxy configuration is read from URL parameters: {@code proxy.type},
 * {@code proxy.host}, {@code proxy.port}, {@code proxy.username}, {@code proxy.password}.
 */
public class ProxyNettyConnectionClient extends AbstractNettyConnectionClient {

    private static final Method GET_OR_ADD_CHANNEL;
    private static final Constructor<?> NETTY_CONFIG_OPERATOR_CTOR;

    static {
        try {
            Class<?> nettyChannelClass = Class.forName("org.apache.dubbo.remoting.transport.netty4.NettyChannel");
            GET_OR_ADD_CHANNEL = nettyChannelClass.getDeclaredMethod(
                    "getOrAddChannel", io.netty.channel.Channel.class, URL.class, ChannelHandler.class);
            GET_OR_ADD_CHANNEL.setAccessible(true);

            Class<?> configOperatorClass = Class.forName("org.apache.dubbo.remoting.transport.netty4.NettyConfigOperator");
            NETTY_CONFIG_OPERATOR_CTOR = configOperatorClass.getConstructor(nettyChannelClass, ChannelHandler.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Bootstrap bootstrap;
    private AtomicReference<Promise<Void>> channelInitializedPromiseRef;
    private AtomicReference<Promise<Void>> connectionPrefaceReceivedPromiseRef;

    public ProxyNettyConnectionClient(URL url, ChannelHandler handler) throws RemotingException {
        super(url, handler);
    }

    @Override
    protected void initConnectionClient() {
        protocol = getUrl().getOrDefaultFrameworkModel()
                .getExtensionLoader(WireProtocol.class)
                .getExtension(getUrl().getProtocol());
        super.initConnectionClient();
    }

    @Override
    protected void initBootstrap() {
        channelInitializedPromiseRef = new AtomicReference<>();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap
                .group(NettyEventLoopFactory.NIO_EVENT_LOOP_GROUP.get())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .remoteAddress(getConnectAddress())
                .channel(socketChannelClass());

        NettyConnectionHandler connectionHandler = new NettyConnectionHandler(this);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getConnectTimeout());
        SslContext sslContext = SslContexts.buildClientSslContext(getUrl());

        URL url = getUrl();
        String proxyType = url.getParameter("proxy.type");
        String proxyHost = url.getParameter("proxy.host");
        int proxyPort = url.getParameter("proxy.port", 0);
        String proxyUsername = url.getParameter("proxy.username");
        String proxyPassword = url.getParameter("proxy.password");
        boolean hasProxy = proxyType != null && !proxyType.isEmpty();

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                Object nettyChannel;
                try {
                    nettyChannel = GET_OR_ADD_CHANNEL.invoke(null, ch, getUrl(), getChannelHandler());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to invoke NettyChannel.getOrAddChannel", e);
                }

                ChannelPipeline pipeline = ch.pipeline();
                NettySslContextOperator nettySslContextOperator = new NettySslContextOperator();

                if (sslContext != null) {
                    pipeline.addLast("negotiation", new SslClientTlsHandler(sslContext));
                }

                int heartbeat = UrlUtils.getHeartbeat(getUrl());
                pipeline.addLast("client-idle-handler", new IdleStateHandler(heartbeat, 0, 0, MILLISECONDS));

                pipeline.addLast(Constants.CONNECTION_HANDLER_NAME, connectionHandler);

                ChannelOperator operator;
                try {
                    operator = (ChannelOperator) NETTY_CONFIG_OPERATOR_CTOR.newInstance(nettyChannel, getChannelHandler());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create NettyConfigOperator", e);
                }
                protocol.configClientPipeline(getUrl(), operator, nettySslContextOperator);

                ChannelHandlerContext http2FrameCodecHandlerCtx = pipeline.context(Http2FrameCodec.class);
                if (http2FrameCodecHandlerCtx == null) {
                    connectionPrefaceReceivedPromiseRef = null;
                } else {
                    if (connectionPrefaceReceivedPromiseRef == null) {
                        connectionPrefaceReceivedPromiseRef = new AtomicReference<>();
                    }
                    connectionPrefaceReceivedPromiseRef.compareAndSet(
                            null, new DefaultPromise<>(GlobalEventExecutor.INSTANCE));
                    pipeline.addAfter(
                            http2FrameCodecHandlerCtx.name(),
                            "client-connection-preface-handler",
                            new Http2ClientSettingsHandler(connectionPrefaceReceivedPromiseRef));
                }

                if (hasProxy) {
                    ProxyHandler proxyHandler = DubboProxyUtils.createNettyProxyHandler(
                            proxyType, proxyHost, proxyPort, proxyUsername, proxyPassword);
                    pipeline.addFirst("proxy", proxyHandler);
                }

                ch.closeFuture().addListener(channelFuture -> clearNettyChannel());

                Promise<Void> channelInitializedPromise = channelInitializedPromiseRef.get();
                if (channelInitializedPromise != null) {
                    channelInitializedPromise.trySuccess(null);
                }
            }
        });
        this.bootstrap = bootstrap;
    }

    @Override
    protected ChannelFuture performConnect() {
        return bootstrap.connect();
    }

    @Override
    protected void doConnect() throws RemotingException {
        long start = System.currentTimeMillis();
        channelInitializedPromiseRef.compareAndSet(null, new DefaultPromise<>(GlobalEventExecutor.INSTANCE));
        super.doConnect();
        waitConnectionPreface(start);
    }

    private void waitConnectionPreface(long start) throws RemotingException {
        Promise<Void> channelInitializedPromise = channelInitializedPromiseRef.get();
        long retainedTimeout = getConnectTimeout() - System.currentTimeMillis() + start;
        boolean ret = channelInitializedPromise.awaitUninterruptibly(retainedTimeout, TimeUnit.MILLISECONDS);
        channelInitializedPromiseRef.set(null);
        if (!ret || !channelInitializedPromise.isSuccess()) {
            throw new RemotingException(
                    this,
                    "client(url: " + getUrl() + ") failed to connect to server " + getConnectAddress()
                            + " client-side channel initialization timeout " + getConnectTimeout() + "ms (elapsed: "
                            + (System.currentTimeMillis() - start) + "ms) from netty client "
                            + NetUtils.getLocalHost() + " using dubbo version " + Version.getVersion());
        }

        if (connectionPrefaceReceivedPromiseRef == null) {
            return;
        }
        Promise<Void> connectionPrefaceReceivedPromise = connectionPrefaceReceivedPromiseRef.get();
        retainedTimeout = getConnectTimeout() - System.currentTimeMillis() + start;
        ret = connectionPrefaceReceivedPromise.awaitUninterruptibly(retainedTimeout, TimeUnit.MILLISECONDS);
        connectionPrefaceReceivedPromiseRef.set(null);
        if (!ret || !connectionPrefaceReceivedPromise.isSuccess()) {
            throw new RemotingException(
                    this,
                    "client(url: " + getUrl() + ") failed to connect to server " + getConnectAddress()
                            + " client-side connection preface timeout " + getConnectTimeout() + "ms (elapsed: "
                            + (System.currentTimeMillis() - start) + "ms) from netty client "
                            + NetUtils.getLocalHost() + " using dubbo version " + Version.getVersion());
        }
    }
}
