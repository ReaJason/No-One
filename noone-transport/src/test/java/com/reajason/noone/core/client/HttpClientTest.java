package com.reajason.noone.core.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientTest {

    @Test
    void shouldSendRawBytesWithConfiguredContentType() throws Exception {
        CapturedRequest req = capture(
                HttpClientConfig.builder()
                        .requestMethod("POST")
                        .contentType("application/json; charset=utf-8")
                        .build(),
                "hello"
        );

        assertEquals("POST", req.method());
        assertNotNull(req.contentType());
        assertTrue(req.contentType().startsWith("application/json"));
        assertEquals("hello", req.body());
    }

    @Test
    void shouldDefaultToOctetStreamContentType() throws Exception {
        CapturedRequest req = capture(
                HttpClientConfig.builder()
                        .requestMethod("POST")
                        .build(),
                "raw"
        );

        assertEquals("POST", req.method());
        assertNotNull(req.contentType());
        assertTrue(req.contentType().contains("octet-stream"));
    }

    @Test
    void shouldReturnRawResponseBytes() throws Exception {
        SendResult result = captureAndSend(
                HttpClientConfig.builder()
                        .requestMethod("POST")
                        .build(),
                "req",
                "raw-response-body",
                200
        );

        assertEquals("raw-response-body", new String(result.responsePayload(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldAttachRequestParamsToUrl() throws Exception {
        CapturedRequest req = capture(
                HttpClientConfig.builder()
                        .requestMethod("POST")
                        .requestParams(Map.of("token", "123", "q", "abc"))
                        .build(),
                "payload"
        );

        assertTrue(req.path().contains("token=123"));
        assertTrue(req.path().contains("q=abc"));
    }

    @Test
    void shouldAttachRequestCookiesToCookieHeader() throws Exception {
        CapturedRequest req = capture(
                HttpClientConfig.builder()
                        .requestMethod("POST")
                        .requestCookies(Map.of("sid", "123"))
                        .build(),
                "payload"
        );

        assertNotNull(req.cookie());
        assertTrue(req.cookie().contains("sid=123"));
    }

    @Test
    void shouldMergeRequestCookiesWithExistingCookieHeader() throws Exception {
        CapturedRequest req = capture(
                HttpClientConfig.builder()
                        .requestMethod("POST")
                        .requestHeaders(Map.of("Cookie", "a=1"))
                        .requestCookies(Map.of("b", "2"))
                        .build(),
                "payload"
        );

        assertNotNull(req.cookie());
        assertTrue(req.cookie().contains("a=1"));
        assertTrue(req.cookie().contains("b=2"));
    }

    @Test
    void shouldThrowResponseStatusExceptionWhenStatusCodeDoesNotMatchExpectation() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> captureAndSend(
                        HttpClientConfig.builder()
                                .requestMethod("POST")
                                .expectedResponseStatusCode(201)
                                .build(),
                        "payload",
                        "ok",
                        500
                )
        );

        assertEquals(201, exception.getExpectedStatusCode());
        assertEquals(500, exception.getActualStatusCode());
    }

    @Test
    void shouldThrowRequestSendExceptionAfterRetryExhausted() throws Exception {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }

        HttpClient client = new HttpClient(
                "http://127.0.0.1:" + unavailablePort + "/test",
                HttpClientConfig.builder()
                        .connectTimeoutMs(200)
                        .readTimeoutMs(200)
                        .maxRetries(1)
                        .retryDelayMs(1L)
                        .build()
        );

        RequestSendException exception = assertThrows(
                RequestSendException.class,
                () -> client.send("payload".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(2, exception.getAttempts());
    }

    @Test
    void shouldThrowRequestInterruptedExceptionWhenRetrySleepIsInterrupted() throws Exception {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }

        HttpClient client = new HttpClient(
                "http://127.0.0.1:" + unavailablePort + "/test",
                HttpClientConfig.builder()
                        .connectTimeoutMs(200)
                        .readTimeoutMs(200)
                        .maxRetries(2)
                        .retryDelayMs(100L)
                        .build()
        );

        Thread.currentThread().interrupt();
        try {
            assertThrows(
                    RequestInterruptedException.class,
                    () -> client.send("payload".getBytes(StandardCharsets.UTF_8))
            );
        } finally {
            Thread.interrupted();
        }
    }

    private CapturedRequest capture(HttpClientConfig config, String payload) throws Exception {
        SendResult result = captureAndSend(config, payload, "ok", 200);
        return result.request();
    }

    private SendResult captureAndSend(
            HttpClientConfig config,
            String payload,
            String responseBody,
            int statusCode
    ) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CompletableFuture<CapturedRequest> captured = new CompletableFuture<>();
        server.createContext("/test", exchange -> {
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            captured.complete(new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    contentType,
                    body,
                    cookie
            ));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/test";
            HttpClient client = new HttpClient(url, config);
            byte[] responsePayload = client.send(payload.getBytes(StandardCharsets.UTF_8));
            CapturedRequest request = captured.get(5, TimeUnit.SECONDS);
            return new SendResult(request, responsePayload);
        } finally {
            server.stop(0);
        }
    }

    private record CapturedRequest(String method, String path, String contentType, String body, String cookie) {
    }

    private record SendResult(CapturedRequest request, byte[] responsePayload) {
    }
}
