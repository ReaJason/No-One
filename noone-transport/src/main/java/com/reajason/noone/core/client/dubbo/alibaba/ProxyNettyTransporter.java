package com.reajason.noone.core.client.dubbo.alibaba;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.Client;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.Server;
import com.alibaba.dubbo.remoting.Transporter;

/**
 * Alibaba Dubbo SPI {@link Transporter} that creates proxy-aware Netty clients.
 * Register via {@code META-INF/dubbo/com.alibaba.dubbo.remoting.Transporter}
 * with key {@code proxy-netty}.
 */
public class ProxyNettyTransporter implements Transporter {

    @Override
    public Server bind(URL url, ChannelHandler handler) throws RemotingException {
        throw new UnsupportedOperationException("proxy-netty transporter does not support bind");
    }

    @Override
    public Client connect(URL url, ChannelHandler handler) throws RemotingException {
        return new ProxyNettyClient(url, handler);
    }
}
