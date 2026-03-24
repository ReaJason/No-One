package com.reajason.noone.core.client;

import com.reajason.noone.core.exception.RequestInterruptedException;
import com.reajason.noone.core.exception.RequestSendException;
import com.reajason.noone.core.exception.RequestSerializeException;
import com.reajason.noone.core.exception.ResponseDecodeException;
import com.reajason.noone.core.profile.config.HttpRequestBodyType;
import com.reajason.noone.core.profile.config.HttpResponseBodyType;
import com.reajason.noone.core.transform.TrafficTransformer;
import com.reajason.noone.core.transform.TransformationSpec;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import okhttp3.*;
import okio.ByteString;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@NoArgsConstructor
public class WebSocketClient implements Client {

    @Setter
    @Getter
    private String url;

    @Setter
    @Getter
    private ClientConfig config;

    private OkHttpClient client;
    private volatile WebSocket webSocket;
    private volatile boolean connected;
    private volatile CompletableFuture<Void> connectFuture;
    private volatile CompletableFuture<byte[]> pendingResponse;
    private final Object sendLock = new Object();

    public WebSocketClient(String url) {
        this.url = url;
        this.config = ClientConfig.builder().build();
        this.client = buildClient();
    }

    public WebSocketClient(String url, ClientConfig config) {
        this.url = url;
        this.config = config != null ? config : ClientConfig.builder().build();
        this.client = buildClient();
    }

    private OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(config.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getWriteTimeoutMs(), TimeUnit.MILLISECONDS);

        if (config.getProxy() != null) {
            ClientConfig.ProxyConfig proxyConfig = config.getProxy();
            builder.proxy(proxyConfig.toJavaProxy());
            if (proxyConfig.hasAuth()) {
                builder.proxyAuthenticator((route, response) -> {
                    String credential = Credentials.basic(proxyConfig.getUsername(), proxyConfig.getPassword());
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                });
            }
        }

        if (config.isSkipSslVerify()) {
            configureInsecureSsl(builder);
        }

        return builder.build();
    }

    private void configureInsecureSsl(OkHttpClient.Builder builder) {
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
            builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure insecure SSL", e);
        }
    }

    @Override
    public boolean connect() {
        if (connected && webSocket != null) {
            return true;
        }

        CompletableFuture<Void> localConnectFuture = new CompletableFuture<>();
        connectFuture = localConnectFuture;
        Request.Builder requestBuilder = new Request.Builder().url(url);
        applyHandshakeHeaders(requestBuilder);

        webSocket = client.newWebSocket(requestBuilder.build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                connected = true;
                localConnectFuture.complete(null);
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                CompletableFuture<byte[]> pending = pendingResponse;
                if (pending != null && !pending.isDone()) {
                    pending.complete(bytes.toByteArray());
                }
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                connected = false;
                ws.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                connected = false;
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                connected = false;
                localConnectFuture.completeExceptionally(t);
                CompletableFuture<byte[]> pending = pendingResponse;
                if (pending != null && !pending.isDone()) {
                    pending.completeExceptionally(t);
                }
            }
        });

        try {
            connectFuture.get(config.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            disconnect();
            throw new RequestSendException("WebSocket handshake timed out", 1, e);
        } catch (ExecutionException e) {
            disconnect();
            throw new RequestSendException("WebSocket handshake failed: " + e.getCause().getMessage(), 1, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RequestInterruptedException("WebSocket handshake was interrupted", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            try {
                ws.close(1000, "disconnect");
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public byte[] send(String payload) {
        return send(payload != null ? payload.getBytes(StandardCharsets.UTF_8) : new byte[0]);
    }

    @Override
    public byte[] send(byte[] payload) {
        synchronized (sendLock) {
            return doSend(payload, true);
        }
    }

    private byte[] doSend(byte[] payload, boolean allowReconnect) {
        if (!connected) {
            connect();
        }

        TransformationSpec requestSpec;
        byte[] messageBytes;
        try {
            requestSpec = TransformationSpec.parse(config.getRequestTransformations());
            byte[] transformedBytes = TrafficTransformer.outbound(
                    payload, requestSpec, config.getTransformerPassword()
            );
            String messageTemplate = config.getRequestTemplate();
            if (messageTemplate == null || messageTemplate.isBlank()) {
                messageBytes = transformedBytes;
            } else {
                messageBytes = HttpBodyTemplateEngine.encodeRequestBody(
                        HttpRequestBodyType.BINARY, messageTemplate, transformedBytes
                ).bytes();
            }
        } catch (RuntimeException e) {
            throw new RequestSerializeException("Failed to prepare WebSocket message payload", e);
        }

        pendingResponse = new CompletableFuture<>();
        boolean sent = webSocket.send(ByteString.of(messageBytes));
        if (!sent) {
            if (allowReconnect) {
                reconnect();
                return doSend(payload, false);
            }
            throw new RequestSendException("Failed to send WebSocket binary message after reconnect", 2, null);
        }

        byte[] responseBytes;
        try {
            responseBytes = pendingResponse.get(config.getReadTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RequestSendException("WebSocket response timed out", 1, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (allowReconnect && !connected) {
                reconnect();
                return doSend(payload, false);
            }
            throw new RequestSendException("WebSocket message failed: " + cause.getMessage(), 1, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RequestInterruptedException("WebSocket response wait was interrupted", e);
        }

        byte[] extractedBytes = HttpBodyTemplateEngine.extractResponsePayloadBytes(
                HttpResponseBodyType.BINARY, config.getResponseTemplate(), responseBytes
        );
        if (extractedBytes == null) {
            throw new ResponseDecodeException("Failed to extract payload from WebSocket response");
        }

        TransformationSpec responseSpec = TransformationSpec.parse(config.getResponseTransformations());
        byte[] inbound;
        try {
            inbound = TrafficTransformer.inbound(
                    extractedBytes, responseSpec, config.getTransformerPassword()
            );
        } catch (RuntimeException e) {
            throw new ResponseDecodeException("Failed to decode WebSocket response payload", e);
        }
        if (inbound == null) {
            throw new ResponseDecodeException("Decoded WebSocket response payload is null");
        }
        return inbound;
    }

    private void reconnect() {
        disconnect();
        connect();
    }

    private void applyHandshakeHeaders(Request.Builder requestBuilder) {
        Map<String, String> headers = config.getRequestHeaders();
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (header.getKey() == null) {
                continue;
            }
            requestBuilder.header(header.getKey(), header.getValue());
        }
    }
}
