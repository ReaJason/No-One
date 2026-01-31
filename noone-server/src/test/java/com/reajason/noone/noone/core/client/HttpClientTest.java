package com.reajason.noone.noone.core.client;

import com.reajason.noone.core.client.ClientConfig;
import com.reajason.noone.core.client.HttpClient;
import com.reajason.noone.core.client.HttpRequestBodyType;
import com.reajason.noone.core.client.HttpResponseBodyType;
import com.reajason.noone.core.transform.TrafficTransformer;
import com.reajason.noone.core.transform.TransformationSpec;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientTest {

    @Test
    void shouldSendUrlEncodedFormByDefault() throws Exception {
        CapturedRequest req = capture(
                ClientConfig.builder()
                        .requestMethod("POST")
                        .requestBodyType(HttpRequestBodyType.FORM_URLENCODED)
                        .build(),
                "abc"
        );

        assertEquals("POST", req.method());
        assertNotNull(req.contentType());
        assertTrue(req.contentType().startsWith("application/x-www-form-urlencoded"));
        assertEquals("username=admin&action=login&q=abc&token=123456", req.body());
    }

    @Test
    void shouldSendMultipartWhenConfigured() throws Exception {
        CapturedRequest req = capture(
                ClientConfig.builder()
                        .requestMethod("POST")
                        .requestBodyType(HttpRequestBodyType.MULTIPART_FORM_DATA)
                        .requestTemplate("""
                                --{{boundary}}
                                Content-Disposition: form-data; name="payload"
                                
                                {{payload}}
                                --{{boundary}}--
                                """)
                        .build(),
                "abc"
        );

        assertEquals("POST", req.method());
        assertNotNull(req.contentType());
        assertTrue(req.contentType().startsWith("multipart/form-data"));
        assertTrue(req.body().contains("name=\"payload\""));
        assertTrue(req.body().contains("abc"));
    }

    @Test
    void shouldSendRawBodyWithTemplateAndContentType() throws Exception {
        CapturedRequest req = capture(
                ClientConfig.builder()
                        .requestMethod("POST")
                        .requestBodyType(HttpRequestBodyType.JSON)
                        .requestTemplate("{\"payload\":\"{{payload}}\"}")
                        .build(),
                "abc"
        );

        assertEquals("POST", req.method());
        assertNotNull(req.contentType());
        assertTrue(req.contentType().startsWith("application/json"));
        assertEquals("{\"payload\":\"abc\"}", req.body());
    }

    @Test
    void shouldExtractResponsePayloadWhenTemplateProvided() throws Exception {
        SendResult result = captureAndSend(
                ClientConfig.builder()
                        .requestMethod("POST")
                        .requestBodyType(HttpRequestBodyType.TEXT)
                        .responseBodyType(HttpResponseBodyType.JSON)
                        .responseTemplate("{\"hello\": \"{{payload}}\"}")
                        .build(),
                "req",
                "{\"hello\": \"resp\"}",
                200
        );

        assertEquals("resp", new String(result.responsePayload(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldApplyRequestAndResponseTransformers() throws Exception {
        List<String> transformations = List.of("Gzip", "AES", "Base64");
        String password = "secret";

        ClientConfig config = ClientConfig.builder()
                .requestMethod("POST")
                .requestBodyType(HttpRequestBodyType.TEXT)
                .requestTemplate("{{payload}}")
                .responseBodyType(HttpResponseBodyType.TEXT)
                .responseTemplate("{{payload}}")
                .transformerPassword(password)
                .requestTransformations(transformations)
                .responseTransformations(transformations)
                .build();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test", exchange -> {
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();

            TransformationSpec requestSpec = TransformationSpec.parse(transformations);
            byte[] decodedReq = TrafficTransformer.inbound(bodyBytes, requestSpec, password);
            assertEquals("abc", new String(decodedReq, StandardCharsets.UTF_8));

            TransformationSpec responseSpec = TransformationSpec.parse(transformations);
            byte[] encodedResp = TrafficTransformer.outbound("resp".getBytes(StandardCharsets.UTF_8), responseSpec, password);

            exchange.sendResponseHeaders(200, encodedResp.length);
            exchange.getResponseBody().write(encodedResp);
            exchange.close();
        });
        server.start();

        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/test";
            HttpClient client = new HttpClient(url, config);
            byte[] responsePayload = client.send("abc");
            assertEquals("resp", new String(responsePayload, StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldAttachRequestParamsToUrl() throws Exception {
        CapturedRequest req = capture(
                ClientConfig.builder()
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
                ClientConfig.builder()
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
                ClientConfig.builder()
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

    private CapturedRequest capture(ClientConfig config, String payload) throws Exception {
        SendResult result = captureAndSend(config, payload, "ok", 200);
        return result.request();
    }

    private SendResult captureAndSend(
            ClientConfig config,
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
            byte[] responsePayload = client.send(payload);
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
