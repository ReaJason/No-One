package com.reajason.noone.core.client.dubbo.apache;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.api.connection.AbstractConnectionClient;
import org.apache.dubbo.remoting.api.pu.AbstractPortUnificationServer;
import org.apache.dubbo.remoting.api.pu.PortUnificationTransporter;

/**
 * Apache Dubbo 3.x SPI {@link PortUnificationTransporter} that creates proxy-aware
 * connection clients for tri:// and dubbo:// protocols.
 * <p>
 * Bypasses the default {@code ConnectionManager} to directly create
 * {@link ProxyNettyConnectionClient} instances with proxy handler injection.
 */
public class ProxyPortUnificationTransporter implements PortUnificationTransporter {

    @Override
    public AbstractPortUnificationServer bind(URL url, ChannelHandler handler) throws RemotingException {
        throw new UnsupportedOperationException("proxy-netty PortUnificationTransporter does not support bind");
    }

    @Override
    public AbstractConnectionClient connect(URL url, ChannelHandler handler) throws RemotingException {
        return new ProxyNettyConnectionClient(url, handler);
    }
}
