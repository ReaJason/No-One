package com.reajason.noone.core.client;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import okhttp3.*;
import okio.ByteString;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * WebSocket client implementation. Pure transport -- no template engine or traffic transformation.
 *
 * @author ReaJason
 */
@NoArgsConstructor
public class WebSocketClient implements Client {

    @Setter
    @Getter
    private String url;

    @Setter
    @Getter
    private WebSocketClientConfig config;

    private OkHttpClient client;
    private volatile WebSocket webSocket;
    private volatile boolean connected;
    private volatile CompletableFuture<Void> connectFuture;
    private volatile CompletableFuture<byte[]> pendingResponse;
    private final Object sendLock = new Object();

    public WebSocketClient(String url, WebSocketClientConfig config) {
        this.url = url;
        this.config = config != null ? config : WebSocketClientConfig.builder().build();
        this.client = buildClient();
    }

    private OkHttpClient buildClient() {
        return OkHttpSupport.build(
                config.getProxy(),
                config.getConnectTimeoutMs(),
                config.getReadTimeoutMs(),
                config.getWriteTimeoutMs(),
                config.isSkipSslVerify()
        );
    }

    @Override
    public boolean connect() {
        if (connected && webSocket != null) {
            return true;
        }

        CompletableFuture<Void> localConnectFuture = new CompletableFuture<>();
        connectFuture = localConnectFuture;
        Request.Builder requestBuilder = new Request.Builder().url(url);
        OkHttpSupport.applyRequestHeaders(requestBuilder, config.getRequestHeaders());

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
    public byte[] send(byte[] payload) {
        synchronized (sendLock) {
            return doSend(payload, true);
        }
    }

    private byte[] doSend(byte[] payload, boolean allowReconnect) {
        if (!connected) {
            connect();
        }

        pendingResponse = new CompletableFuture<>();
        boolean sent = webSocket.send(ByteString.of(payload));
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

        return responseBytes;
    }

    private void reconnect() {
        disconnect();
        connect();
    }
}
