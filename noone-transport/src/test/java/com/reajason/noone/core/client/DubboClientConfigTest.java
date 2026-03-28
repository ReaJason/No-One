package com.reajason.noone.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DubboClientConfigTest {
    @Test
    void defaultConfigHasNoProxy() {
        DubboClientConfig config = DubboClientConfig.builder().build();
        assertNull(config.getProxy());
    }

    @Test
    void configWithProxy() {
        ProxyConfig proxy = ProxyConfig.builder()
                .type("SOCKS5").host("127.0.0.1").port(1080).build();
        DubboClientConfig config = DubboClientConfig.builder().proxy(proxy).build();
        assertNotNull(config.getProxy());
        assertEquals("SOCKS5", config.getProxy().getType());
        assertEquals("127.0.0.1", config.getProxy().getHost());
        assertEquals(1080, config.getProxy().getPort());
    }
}
