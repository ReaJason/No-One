package com.reajason.noone.core.client;

import com.reajason.noone.core.exception.RequestSendException;
import com.reajason.noone.core.exception.RequestSerializeException;
import com.reajason.noone.core.transform.TrafficTransformer;
import com.reajason.noone.core.transform.TransformationSpec;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketClientTest {

    @Test
    void shouldConnectAndDisconnect() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new EchoWebSocketListener())
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    ClientConfig.builder()
                            .connectTimeoutMs(5000)
                            .readTimeoutMs(5000)
                            .build()
            );

            assertTrue(client.connect());
            assertTrue(client.isConnected());

            client.disconnect();
            assertFalse(client.isConnected());
        }
    }

    @Test
    void shouldReturnTrueWhenAlreadyConnected() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new EchoWebSocketListener())
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    ClientConfig.builder()
                            .connectTimeoutMs(5000)
                            .readTimeoutMs(5000)
                            .build()
            );

            assertTrue(client.connect());
            assertTrue(client.connect());
            client.disconnect();
        }
    }

    @Test
    void shouldSendAndReceiveBinaryMessage() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new EchoWebSocketListener())
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    ClientConfig.builder()
                            .connectTimeoutMs(5000)
                            .readTimeoutMs(5000)
                            .build()
            );

            byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
            byte[] response = client.send(payload);
            assertEquals("hello", new String(response, StandardCharsets.UTF_8));

            client.disconnect();
        }
    }

    @Test
    void shouldSendStringPayload() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new EchoWebSocketListener())
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    ClientConfig.builder()
                            .connectTimeoutMs(5000)
                            .readTimeoutMs(5000)
                            .build()
            );

            byte[] response = client.send("hello");
            assertEquals("hello", new String(response, StandardCharsets.UTF_8));

            client.disconnect();
        }
    }

    @Test
    void shouldAutoConnectOnFirstSend() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new EchoWebSocketListener())
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    ClientConfig.builder()
                            .connectTimeoutMs(5000)
                            .readTimeoutMs(5000)
                            .build()
            );

            assertFalse(client.isConnected());
            byte[] response = client.send("hello".getBytes(StandardCharsets.UTF_8));
            assertTrue(client.isConnected());
            assertEquals("hello", new String(response, StandardCharsets.UTF_8));

            client.disconnect();
        }
    }

    @Test
    void shouldApplyMessageAndResponseTemplates() throws Exception {
        String messageTemplate = "<base64>AAAA</base64>{{payload}}<base64>BBBB</base64>";
        String responseTemplate = "<base64>CCCC</base64>{{payload}}<base64>DDDD</base64>";

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new WebSocketListener() {
                        @Override
                        public void onMessage(WebSocket webSocket, ByteString bytes) {
                            byte[] extracted = HttpBodyTemplateEngine.extractResponsePayloadBytes(
                                    HttpResponseBodyType.BINARY, messageTemplate, bytes.toByteArray()
                            );
                            byte[] encoded = HttpBodyTemplateEngine.encodeRequestBody(
                                    HttpRequestBodyType.BINARY, responseTemplate, extracted
                            ).bytes();
                            webSocket.send(ByteString.of(encoded));
                        }
                    })
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    ClientConfig.builder()
                            .connectTimeoutMs(5000)
                            .readTimeoutMs(5000)
                            .requestTemplate(messageTemplate)
                            .responseTemplate(responseTemplate)
                            .responseBodyType(HttpResponseBodyType.BINARY)
                            .build()
            );

            byte[] payload = "templateTest".getBytes(StandardCharsets.UTF_8);
            byte[] response = client.send(payload);
            assertEquals("templateTest", new String(response, StandardCharsets.UTF_8));

            client.disconnect();
        }
    }

    @Test
    void shouldApplyRequestAndResponseTransformers() throws Exception {
        List<String> transformations = List.of("Gzip", "AES", "Base64");
        String password = "secret";

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new WebSocketListener() {
                        @Override
                        public void onMessage(WebSocket webSocket, ByteString bytes) {
                            TransformationSpec requestSpec = TransformationSpec.parse(transformations);
                            byte[] decoded = TrafficTransformer.inbound(
                                    bytes.toByteArray(), requestSpec, password
                            );
                            assertEquals("abc", new String(decoded, StandardCharsets.UTF_8));

                            TransformationSpec responseSpec = TransformationSpec.parse(transformations);
                            byte[] encoded = TrafficTransformer.outbound(
                                    "resp".getBytes(StandardCharsets.UTF_8), responseSpec, password
                            );
                            webSocket.send(ByteString.of(encoded));
                        }
                    })
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    ClientConfig.builder()
                            .connectTimeoutMs(5000)
                            .readTimeoutMs(5000)
                            .transformerPassword(password)
                            .requestTransformations(transformations)
                            .responseTransformations(transformations)
                            .build()
            );

            byte[] response = client.send("abc".getBytes(StandardCharsets.UTF_8));
            assertEquals("resp", new String(response, StandardCharsets.UTF_8));

            client.disconnect();
        }
    }

    @Test
    void shouldReconnectWhenConnectionDrops() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            // First connection - will be closed by server after first message
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new WebSocketListener() {
                        @Override
                        public void onMessage(WebSocket webSocket, ByteString bytes) {
                            webSocket.send(bytes);
                            webSocket.close(1000, "done");
                        }
                    })
                    .build());
            // Second connection for reconnect
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new EchoWebSocketListener())
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    ClientConfig.builder()
                            .connectTimeoutMs(5000)
                            .readTimeoutMs(5000)
                            .build()
            );

            byte[] response1 = client.send("first".getBytes(StandardCharsets.UTF_8));
            assertEquals("first", new String(response1, StandardCharsets.UTF_8));

            // Wait for server to close the connection
            Thread.sleep(200);

            byte[] response2 = client.send("second".getBytes(StandardCharsets.UTF_8));
            assertEquals("second", new String(response2, StandardCharsets.UTF_8));

            client.disconnect();
        }
    }

    @Test
    void shouldThrowRequestSendExceptionWhenHandshakeTimesOut() throws Exception {
        int unavailablePort;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }

        WebSocketClient client = new WebSocketClient(
                "ws://127.0.0.1:" + unavailablePort + "/ws",
                ClientConfig.builder()
                        .connectTimeoutMs(200)
                        .readTimeoutMs(200)
                        .build()
        );

        assertThrows(
                RequestSendException.class,
                () -> client.send("payload".getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void shouldThrowRequestSerializeExceptionWhenTransformConfigIsInvalid() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new EchoWebSocketListener())
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    ClientConfig.builder()
                            .connectTimeoutMs(5000)
                            .readTimeoutMs(5000)
                            .requestTransformations(List.of("gzip"))
                            .build()
            );

            assertThrows(
                    RequestSerializeException.class,
                    () -> client.send("payload".getBytes(StandardCharsets.UTF_8))
            );

            client.disconnect();
        }
    }

    @Test
    void shouldSendMultipleMessagesOnSameConnection() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new EchoWebSocketListener())
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    ClientConfig.builder()
                            .connectTimeoutMs(5000)
                            .readTimeoutMs(5000)
                            .build()
            );

            for (int i = 0; i < 5; i++) {
                String msg = "msg-" + i;
                byte[] response = client.send(msg.getBytes(StandardCharsets.UTF_8));
                assertEquals(msg, new String(response, StandardCharsets.UTF_8));
            }

            client.disconnect();
        }
    }

    static class EchoWebSocketListener extends WebSocketListener {
        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            webSocket.send(bytes);
        }
    }
}
