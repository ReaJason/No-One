package com.reajason.noone.core.client;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * WebSocket client implementation with configurable proxy, headers, timeout, and SSL settings.
 *
 * @author ReaJason
 * @since 2025/12/13
 */
@NoArgsConstructor
public class WebSocketClient implements Client {

    @Setter
    @Getter
    private String url;

    @Setter
    @Getter
    private ClientConfig config;

    private org.java_websocket.client.WebSocketClient wsClient;
    private CompletableFuture<String> pendingResponse;

    public WebSocketClient(String url) {
        this.url = url;
        this.config = ClientConfig.builder().build();
    }

    public WebSocketClient(String url, ClientConfig config) {
        this.url = url;
        this.config = config != null ? config : ClientConfig.builder().build();
    }

    @Override
    @SneakyThrows
    public boolean connect() {
        if (isConnected()) {
            return true;
        }

        int connectTimeoutSec = config.getConnectTimeoutMs() / 1000;
        if (connectTimeoutSec <= 0) {
            connectTimeoutSec = 10;
        }

        CountDownLatch connectLatch = new CountDownLatch(1);
        final boolean[] connected = {false};

        // Prepare headers for handshake
        Map<String, String> headers = new HashMap<>();
        if (config.getRequestHeaders() != null) {
            headers.putAll(config.getRequestHeaders());
        }

        URI uri = new URI(url);
        wsClient = new org.java_websocket.client.WebSocketClient(uri, new Draft_6455(), headers, config.getConnectTimeoutMs()) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                connected[0] = true;
                connectLatch.countDown();
            }

            @Override
            public void onMessage(String message) {
                if (pendingResponse != null) {
                    pendingResponse.complete(message);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                if (pendingResponse != null) {
                    pendingResponse.completeExceptionally(
                            new IOException("WebSocket closed: " + reason));
                }
                connectLatch.countDown();
            }

            @Override
            public void onError(Exception ex) {
                if (pendingResponse != null) {
                    pendingResponse.completeExceptionally(ex);
                }
                connectLatch.countDown();
            }
        };

        // Configure proxy if specified
        if (config.getProxy() != null) {
            ClientConfig.ProxyConfig proxyConfig = config.getProxy();
            Proxy proxy = proxyConfig.toJavaProxy();
            wsClient.setProxy(proxy);
        }

        // Configure SSL for wss:// connections
        if (url.startsWith("wss://") && config.isSkipSslVerify()) {
            configureInsecureSsl();
        }

        wsClient.connect();
        connectLatch.await(connectTimeoutSec, TimeUnit.SECONDS);
        return connected[0];
    }

    private void configureInsecureSsl() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            wsClient.setSocketFactory(sslContext.getSocketFactory());
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure insecure SSL for WebSocket", e);
        }
    }

    @Override
    public void disconnect() {
        if (wsClient != null) {
            wsClient.close();
            wsClient = null;
        }
    }

    @Override
    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen();
    }

    @Override
    public byte[] send(String payload) {
        // Auto-connect if not connected
        if (!isConnected()) {
            if (!connect()) {
                return null;
            }
        }

        int responseTimeoutSec = config.getReadTimeoutMs() / 1000;
        if (responseTimeoutSec <= 0) {
            responseTimeoutSec = 30;
        }

        try {
            pendingResponse = new CompletableFuture<>();
            wsClient.send(payload);
            return pendingResponse.get(responseTimeoutSec, TimeUnit.SECONDS).getBytes(StandardCharsets.UTF_8);
        } catch (TimeoutException e) {
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            pendingResponse = null;
        }
    }

    @Override
    public byte[] send(byte[] payload) {
        // Auto-connect if not connected
        if (!isConnected()) {
            if (!connect()) {
                return null;
            }
        }

        int responseTimeoutSec = config.getReadTimeoutMs() / 1000;
        if (responseTimeoutSec <= 0) {
            responseTimeoutSec = 30;
        }

        try {
            pendingResponse = new CompletableFuture<>();
            wsClient.send(payload);
            return pendingResponse.get(responseTimeoutSec, TimeUnit.SECONDS).getBytes(StandardCharsets.UTF_8);
        } catch (TimeoutException e) {
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            pendingResponse = null;
        }
    }
}
