package com.reajason.noone.core.client;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

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
                    WebSocketClientConfig.builder()
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
                    WebSocketClientConfig.builder()
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
                    WebSocketClientConfig.builder()
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
    void shouldAutoConnectOnFirstSend() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new EchoWebSocketListener())
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    WebSocketClientConfig.builder()
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
    void shouldReconnectWhenConnectionDrops() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new WebSocketListener() {
                        @Override
                        public void onMessage(WebSocket webSocket, ByteString bytes) {
                            webSocket.send(bytes);
                            webSocket.close(1000, "done");
                        }

                        @Override
                        public void onClosing(WebSocket webSocket, int code, String reason) {
                            webSocket.close(code, reason);
                        }
                    })
                    .build());
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new EchoWebSocketListener())
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    WebSocketClientConfig.builder()
                            .connectTimeoutMs(5000)
                            .readTimeoutMs(5000)
                            .build()
            );

            byte[] response1 = client.send("first".getBytes(StandardCharsets.UTF_8));
            assertEquals("first", new String(response1, StandardCharsets.UTF_8));

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
                WebSocketClientConfig.builder()
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
    void shouldSendMultipleMessagesOnSameConnection() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .webSocketUpgrade(new EchoWebSocketListener())
                    .build());
            server.start();

            WebSocketClient client = new WebSocketClient(
                    server.url("/ws").toString(),
                    WebSocketClientConfig.builder()
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

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(code, reason);
        }
    }
}
