package com.reajason.noone.server.shell;

import com.reajason.noone.core.client.HttpClient;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.core.profile.config.HttpProtocolConfig;
import com.reajason.noone.core.profile.config.ProtocolType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClientFactoryCompatTest {

    @Test
    void shouldApplyLegacyShellClientSettingsWhenClientConfigIsMissing() {
        Shell shell = new Shell();
        shell.setUrl("http://target.example.com/api");
        shell.setProxyUrl("http://legacy-proxy:8080");
        shell.setCustomHeaders(Map.of("X-Legacy", "yes"));
        shell.setConnectTimeoutMs(1500);
        shell.setReadTimeoutMs(2500);
        shell.setSkipSslVerify(true);
        shell.setMaxRetries(2);
        shell.setRetryDelayMs(3000L);

        HttpClient client = assertInstanceOf(HttpClient.class, ClientFactory.create(shell, httpProfile()));

        assertNotNull(client.getConfig().getProxy());
        assertEquals("HTTP", client.getConfig().getProxy().getType());
        assertEquals("legacy-proxy", client.getConfig().getProxy().getHost());
        assertEquals(8080, client.getConfig().getProxy().getPort());
        assertEquals(Map.of("X-Legacy", "yes"), client.getConfig().getRequestHeaders());
        assertEquals(1500, client.getConfig().getConnectTimeoutMs());
        assertEquals(2500, client.getConfig().getReadTimeoutMs());
        assertEquals(true, client.getConfig().isSkipSslVerify());
        assertEquals(2, client.getConfig().getMaxRetries());
        assertEquals(3000L, client.getConfig().getRetryDelayMs());
    }

    @Test
    void shouldLetClientConfigOverrideLegacyShellClientSettings() {
        Shell shell = new Shell();
        shell.setUrl("http://target.example.com/api");
        shell.setProxyUrl("http://legacy-proxy:8080");
        shell.setCustomHeaders(Map.of("X-Legacy", "yes"));
        shell.setConnectTimeoutMs(1500);
        shell.setClientConfig(new LinkedHashMap<>(Map.of(
                "proxyUrl", "http://new-proxy:9090",
                "customHeaders", Map.of("X-New", "yes"),
                "connectTimeoutMs", 2600
        )));

        HttpClient client = assertInstanceOf(HttpClient.class, ClientFactory.create(shell, httpProfile()));

        assertEquals("new-proxy", client.getConfig().getProxy().getHost());
        assertEquals(9090, client.getConfig().getProxy().getPort());
        assertEquals(Map.of("X-New", "yes"), client.getConfig().getRequestHeaders());
        assertEquals(2600, client.getConfig().getConnectTimeoutMs());
    }

    private Profile httpProfile() {
        Profile profile = new Profile();
        profile.setProtocolType(ProtocolType.HTTP);
        profile.setProtocolConfig(new HttpProtocolConfig());
        return profile;
    }
}
