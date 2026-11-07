package com.reajason.noone.core.client;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end mutual TLS: the server demands a client certificate, so these tests only pass when the
 * configured PKCS#12 keystore really reaches the TLS stack -- with and without impersonation.
 */
class MutualTlsTest {

    private static final String PASSWORD = "test-password";
    private static final KeyPair SERVER_KEY;
    private static final X509Certificate SERVER_CERTIFICATE;
    private static final KeyPair CLIENT_KEY;
    private static final X509Certificate CLIENT_CERTIFICATE;
    private static final String CLIENT_KEYSTORE;

    static {
        try {
            SERVER_KEY = TestCertificates.rsaKeyPair();
            SERVER_CERTIFICATE = TestCertificates.selfSigned(SERVER_KEY, "localhost");
            CLIENT_KEY = TestCertificates.rsaKeyPair();
            CLIENT_CERTIFICATE = TestCertificates.selfSigned(CLIENT_KEY, "shell-client");
            CLIENT_KEYSTORE = TestCertificates.pkcs12Base64(CLIENT_KEY, CLIENT_CERTIFICATE, PASSWORD);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void shouldPresentTheClientCertificate() throws Exception {
        assertEquals("ok", sendWith(bareConfig()));
    }

    @Test
    void shouldPresentTheClientCertificateWhileImpersonatingABrowser() throws Exception {
        HttpClientConfig config = HttpClientConfig.builder()
                .clientCertificate(CLIENT_KEYSTORE)
                .clientCertificatePassword(PASSWORD)
                .skipSslVerify(true)
                .browserImpersonation(true)
                .impersonationProfile(BrowserImpersonationProfile.MAC_CHROME)
                .build();

        assertEquals("ok", sendWith(config));
    }

    @Test
    void shouldFailWhenTheServerRequiresACertificateAndNoneIsConfigured() throws Exception {
        try (MockWebServer server = mutualTlsServer()) {
            HttpClient client = new HttpClient(
                    server.url("/test").toString(),
                    HttpClientConfig.builder()
                            .skipSslVerify(true)
                            .connectTimeoutMs(5000)
                            .readTimeoutMs(5000)
                            .build());

            assertThrows(
                    ShellCommunicationException.class,
                    () -> client.send("payload".getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    void shouldFailWhenTheKeystorePasswordIsWrong() throws Exception {
        try (MockWebServer server = mutualTlsServer()) {
            String url = server.url("/test").toString();
            HttpClientConfig config = HttpClientConfig.builder()
                    .clientCertificate(CLIENT_KEYSTORE)
                    .clientCertificatePassword("wrong-password")
                    .skipSslVerify(true)
                    .build();

            // The keystore is unreadable, so the client refuses to start rather than connecting
            // without the certificate the caller asked for.
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new HttpClient(url, config));
        }
    }

    private String sendWith(HttpClientConfig config) throws Exception {
        try (MockWebServer server = mutualTlsServer()) {
            HttpClient client = new HttpClient(server.url("/test").toString(), config);
            byte[] response = client.send("payload".getBytes(StandardCharsets.UTF_8));
            return new String(response, StandardCharsets.UTF_8);
        }
    }

    private HttpClientConfig bareConfig() {
        return HttpClientConfig.builder()
                .clientCertificate(CLIENT_KEYSTORE)
                .clientCertificatePassword(PASSWORD)
                .skipSslVerify(true)
                .build();
    }

    private MockWebServer mutualTlsServer() throws Exception {
        SSLContext serverContext = TestCertificates.serverContext(SERVER_KEY, SERVER_CERTIFICATE, PASSWORD);
        MockWebServer server = new MockWebServer();
        server.useHttps(serverContext.getSocketFactory());
        server.requireClientAuth();
        server.enqueue(new MockResponse.Builder().body("ok").build());
        server.start();
        return server;
    }
}
