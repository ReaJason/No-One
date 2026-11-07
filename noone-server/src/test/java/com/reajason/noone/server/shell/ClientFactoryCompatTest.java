package com.reajason.noone.server.shell;

import com.reajason.noone.core.client.BrowserImpersonationProfile;
import com.reajason.noone.core.client.HttpClient;
import com.reajason.noone.core.client.WebSocketClient;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.core.profile.config.HttpProtocolConfig;
import com.reajason.noone.core.profile.config.ProtocolType;
import com.reajason.noone.core.profile.config.WebSocketProtocolConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void shouldReadBrowserImpersonationFromClientConfig() {
        Shell shell = shellWithConfig(Map.of(
                "browserImpersonation", true,
                "impersonationProfile", "mac-firefox"
        ));

        HttpClient client = assertInstanceOf(HttpClient.class, ClientFactory.create(shell, httpProfile()));

        assertTrue(client.getConfig().isBrowserImpersonation());
        assertEquals(BrowserImpersonationProfile.MAC_FIREFOX,
                client.getConfig().resolveImpersonationProfile());
    }

    @Test
    void shouldDefaultToChromeWhenImpersonationIsEnabledWithoutAProfile() {
        Shell shell = shellWithConfig(Map.of("browserImpersonation", true));

        HttpClient client = assertInstanceOf(HttpClient.class, ClientFactory.create(shell, httpProfile()));

        assertEquals(BrowserImpersonationProfile.DEFAULT,
                client.getConfig().resolveImpersonationProfile());
    }

    @Test
    void shouldIgnoreTheProfileWhenImpersonationIsSwitchedOff() {
        Shell shell = shellWithConfig(Map.of(
                "browserImpersonation", false,
                "impersonationProfile", "android"
        ));

        HttpClient client = assertInstanceOf(HttpClient.class, ClientFactory.create(shell, httpProfile()));

        assertEquals(false, client.getConfig().isBrowserImpersonation());
        assertNull(client.getConfig().resolveImpersonationProfile());
    }

    @Test
    void shouldReadBrowserImpersonationForWebSocketClients() {
        Shell shell = shellWithConfig(Map.of(
                "browserImpersonation", true,
                "impersonationProfile", "safari"
        ));

        WebSocketClient client = assertInstanceOf(
                WebSocketClient.class, ClientFactory.create(shell, webSocketProfile()));

        assertTrue(client.getConfig().isBrowserImpersonation());
        assertEquals(BrowserImpersonationProfile.MAC_SAFARI,
                client.getConfig().resolveImpersonationProfile());
    }

    @Test
    void shouldGiveTheCoreAndLoaderClientsTheSameImpersonationConfig() {
        Shell shell = shellWithConfig(Map.of(
                "browserImpersonation", true,
                "impersonationProfile", "ios"
        ));

        HttpClient coreClient = assertInstanceOf(HttpClient.class, ClientFactory.create(shell, httpProfile()));
        HttpClient loaderClient = assertInstanceOf(HttpClient.class, ClientFactory.create(shell, httpProfile()));

        assertEquals(coreClient.getConfig().isBrowserImpersonation(),
                loaderClient.getConfig().isBrowserImpersonation());
        assertEquals(coreClient.getConfig().resolveImpersonationProfile(),
                loaderClient.getConfig().resolveImpersonationProfile());
        assertEquals(BrowserImpersonationProfile.IOS,
                loaderClient.getConfig().resolveImpersonationProfile());
    }

    @Test
    void shouldRejectAnUnsupportedImpersonationProfile() {
        Shell shell = shellWithConfig(Map.of(
                "browserImpersonation", true,
                "impersonationProfile", "netscape"
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ClientFactory.create(shell, httpProfile()));

        assertTrue(exception.getMessage().contains("netscape"));
    }

    @Test
    void shouldRejectAnInvalidClientCertificate() {
        Shell shell = shellWithConfig(Map.of("clientCertificate", "not a keystore"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ClientFactory.create(shell, httpProfile()));

        assertTrue(exception.getMessage().contains("PKCS#12"));
    }

    @Test
    void shouldRejectAClientCertificatePasswordWithoutACertificate() {
        Shell shell = shellWithConfig(Map.of("clientCertificatePassword", "test-password"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ClientFactory.create(shell, httpProfile()));

        assertTrue(exception.getMessage().contains("clientCertificate"));
    }

    @Test
    void shouldRejectAnInvalidClientCertificateForWebSocketClientsToo() {
        Shell shell = shellWithConfig(Map.of("clientCertificate", "not a keystore"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ClientFactory.create(shell, webSocketProfile()));

        assertTrue(exception.getMessage().contains("PKCS#12"));
    }

    private Shell shellWithConfig(Map<String, Object> clientConfig) {
        Shell shell = new Shell();
        shell.setUrl("https://target.example.com/api");
        shell.setClientConfig(new LinkedHashMap<>(clientConfig));
        return shell;
    }

    private Profile httpProfile() {
        Profile profile = new Profile();
        profile.setProtocolType(ProtocolType.HTTP);
        profile.setProtocolConfig(new HttpProtocolConfig());
        return profile;
    }

    private Profile webSocketProfile() {
        Profile profile = new Profile();
        profile.setProtocolType(ProtocolType.WEBSOCKET);
        profile.setProtocolConfig(new WebSocketProtocolConfig());
        return profile;
    }
}
