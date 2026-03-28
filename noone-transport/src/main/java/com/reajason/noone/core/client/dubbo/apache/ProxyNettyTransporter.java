package com.reajason.noone.core.client.dubbo.apache;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Client;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.RemotingServer;
import org.apache.dubbo.remoting.Transporter;

/**
 * Dubbo SPI {@link Transporter} that creates proxy-aware Netty clients.
 * Register via {@code META-INF/dubbo/org.apache.dubbo.remoting.Transporter}
 * with key {@code proxy-netty}.
 */
public class ProxyNettyTransporter implements Transporter {

    @Override
    public RemotingServer bind(URL url, ChannelHandler handler) throws RemotingException {
        throw new UnsupportedOperationException("proxy-netty transporter does not support bind");
    }

    @Override
    public Client connect(URL url, ChannelHandler handler) throws RemotingException {
        return new ProxyNettyClient(url, handler);
    }
}
