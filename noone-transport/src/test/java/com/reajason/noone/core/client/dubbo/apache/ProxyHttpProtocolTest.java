package com.reajason.noone.core.client.dubbo.apache;

import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import org.apache.dubbo.common.URL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProxyHttpProtocolTest {

    @Test
    void buildJsonRpcHttpClientAppliesHttpProxyAuthAndTimeouts() {
        URL url = URL.valueOf(
                "http://service.example.com/api"
                        + "?proxy.type=HTTP"
                        + "&proxy.host=proxy.example.com"
                        + "&proxy.port=8080"
                        + "&proxy.username=user"
                        + "&proxy.password=pass"
                        + "&timeout=4321");

        JsonRpcHttpClient client = ProxyHttpProtocol.buildJsonRpcHttpClient("http://service.example.com/api", url);

        assertEquals("proxy.example.com", ((java.net.InetSocketAddress) client.getConnectionProxy().address()).getHostString());
        assertEquals(8080, ((java.net.InetSocketAddress) client.getConnectionProxy().address()).getPort());
        assertEquals("Basic dXNlcjpwYXNz", client.getHeaders().get("Proxy-Authorization"));
        assertEquals(4321, client.getReadTimeoutMillis());
        assertEquals(3000, client.getConnectionTimeoutMillis());
    }

    @Test
    void buildJsonRpcHttpClientDoesNotEmitHttpProxyHeaderForSocksAuth() {
        URL url = URL.valueOf(
                "http://service.example.com/api"
                        + "?proxy.type=SOCKS5"
                        + "&proxy.host=socks.example.com"
                        + "&proxy.port=1080"
                        + "&proxy.username=user"
                        + "&proxy.password=pass");

        JsonRpcHttpClient client = ProxyHttpProtocol.buildJsonRpcHttpClient("http://service.example.com/api", url);

        assertEquals("socks.example.com", ((java.net.InetSocketAddress) client.getConnectionProxy().address()).getHostString());
        assertEquals(1080, ((java.net.InetSocketAddress) client.getConnectionProxy().address()).getPort());
        assertNull(client.getHeaders().get("Proxy-Authorization"));
    }
}
