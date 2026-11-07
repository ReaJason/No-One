package com.reajason.noone.core.client;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the impersonation switch changes the request the server receives, and that turning
 * it off leaves the previous request shape untouched.
 */
class BrowserImpersonationTest {

    @Test
    void shouldAddBrowserProfileHeadersWhenEnabled() throws Exception {
        Headers headers = capture(HttpClientConfig.builder()
                .browserImpersonation(true)
                .impersonationProfile(BrowserImpersonationProfile.MAC_CHROME)
                .build());

        String userAgent = headers.getFirst("User-Agent");
        assertNotNull(userAgent);
        assertTrue(userAgent.contains("Chrome/"), "expected Chrome user agent, got " + userAgent);
        assertEquals("?0", headers.getFirst("Sec-Ch-Ua-Mobile"));
        assertEquals("none", headers.getFirst("Sec-Fetch-Site"));
        assertNotNull(headers.getFirst("Sec-Ch-Ua"));
    }

    @Test
    void shouldDefaultToChromeWhenProfileIsNotSelected() throws Exception {
        Headers headers = capture(HttpClientConfig.builder()
                .browserImpersonation(true)
                .build());

        String userAgent = headers.getFirst("User-Agent");
        assertNotNull(userAgent);
        assertTrue(userAgent.contains("Chrome/"), "expected the default Chrome profile, got " + userAgent);
    }

    @Test
    void shouldNotAddBrowserProfileHeadersWhenDisabled() throws Exception {
        Headers headers = capture(HttpClientConfig.builder().build());

        assertFalse(headers.containsKey("Sec-Fetch-Site"));
        assertFalse(headers.containsKey("Sec-Ch-Ua"));
    }

    @Test
    void shouldLetConfiguredHeadersOverrideTheProfileHeaders() throws Exception {
        Headers headers = capture(HttpClientConfig.builder()
                .browserImpersonation(true)
                .impersonationProfile(BrowserImpersonationProfile.MAC_CHROME)
                .requestHeaders(Map.of("User-Agent", "shell-client/1.0", "X-Shell", "yes"))
                .build());

        assertEquals("shell-client/1.0", headers.getFirst("User-Agent"));
        assertEquals("yes", headers.getFirst("X-Shell"));
        // Everything the profile adds and the caller did not pin is still there.
        assertEquals("none", headers.getFirst("Sec-Fetch-Site"));
    }

    @Test
    void shouldKeepTheBinaryPayloadAndContentTypeWhenImpersonating() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CompletableFuture<String> body = new CompletableFuture<>();
        CompletableFuture<String> contentType = new CompletableFuture<>();
        server.createContext("/test", exchange -> {
            body.complete(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentType.complete(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            HttpClient client = new HttpClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/test",
                    HttpClientConfig.builder()
                            .browserImpersonation(true)
                            .impersonationProfile(BrowserImpersonationProfile.MAC_FIREFOX)
                            .contentType("application/octet-stream")
                            .build());

            client.send("payload".getBytes(StandardCharsets.UTF_8));

            assertEquals("payload", body.get(5, TimeUnit.SECONDS));
            assertEquals("application/octet-stream", contentType.get(5, TimeUnit.SECONDS));
        } finally {
            server.stop(0);
        }
    }

    private Headers capture(HttpClientConfig config) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CompletableFuture<Headers> captured = new CompletableFuture<>();
        server.createContext("/test", exchange -> {
            exchange.getRequestBody().readAllBytes();
            Headers copy = new Headers();
            copy.putAll(exchange.getRequestHeaders());
            captured.complete(copy);
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            HttpClient client = new HttpClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/test", config);
            client.send("payload".getBytes(StandardCharsets.UTF_8));
            return captured.get(5, TimeUnit.SECONDS);
        } finally {
            server.stop(0);
        }
    }
}
