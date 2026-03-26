package com.reajason.noone.core.client;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.service.GenericService;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Dubbo RPC client using GenericService for protocol-agnostic invocation.
 * Pure transport -- no traffic transformation logic.
 *
 * @author ReaJason
 */
@NoArgsConstructor
public class DubboClient implements Client {

    @Setter
    @Getter
    private String url;

    @Setter
    @Getter
    private DubboClientConfig config;

    private volatile ReferenceConfig<GenericService> referenceConfig;
    private volatile GenericService genericService;
    private volatile boolean connected;
    private final Object sendLock = new Object();

    public DubboClient(String url, DubboClientConfig config) {
        this.url = url;
        this.config = config != null ? config : DubboClientConfig.builder().build();
    }

    @Override
    public boolean connect() {
        if (connected && genericService != null) {
            return true;
        }

        ReferenceConfig<GenericService> ref = new ReferenceConfig<>();
        try {
            ApplicationConfig applicationConfig = new ApplicationConfig();
            applicationConfig.setName("noone-dubbo-client");
            applicationConfig.setQosEnable(false);
            String interfaceName = resolveInterfaceName();
            ref.setApplication(applicationConfig);
            ref.setInterface(interfaceName);
            ref.setGeneric("true");
            ref.setUrl(url);
            ref.setTimeout(config.getReadTimeoutMs());
            ref.setCheck(false);
            ref.setParameters(Map.of("reconnect", "false"));

            genericService = ref.get();
            referenceConfig = ref;
            connected = true;
            return true;
        } catch (Exception e) {
            connected = false;
            try {
                ref.destroy();
            } catch (Exception ignored) {
            }
            throw new RequestSendException("Dubbo connection failed: " + e.getMessage(), 1, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        genericService = null;
        if (referenceConfig != null) {
            try {
                referenceConfig.destroy();
            } catch (Exception ignored) {
            }
            referenceConfig = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && genericService != null;
    }

    @Override
    public byte[] send(byte[] payload) {
        synchronized (sendLock) {
            return doSend(payload, true);
        }
    }

    private byte[] doSend(byte[] payload, boolean allowReconnect) {
        if (!connected || genericService == null) {
            connect();
        }

        Object result;
        try {
            result = genericService.$invoke(
                    config.getMethodName(),
                    config.getParameterTypes(),
                    new Object[]{payload}
            );
        } catch (Exception e) {
            if (isInterruptedFailure(e)) {
                Thread.currentThread().interrupt();
                throw new RequestInterruptedException("Dubbo invocation was interrupted", e);
            }
            if (allowReconnect) {
                reconnect();
                return doSend(payload, false);
            }
            throw new RequestSendException("Dubbo invocation failed after reconnect: " + e.getMessage(), 2, e);
        }

        return convertResult(result);
    }

    private String resolveInterfaceName() {
        if (config.getInterfaceName() != null && !config.getInterfaceName().isEmpty()) {
            return config.getInterfaceName();
        }
        try {
            String path = URI.create(url).getPath();
            if (path != null && path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path != null && !path.isEmpty()) {
                return path;
            }
        } catch (Exception ignored) {
        }
        throw new IllegalStateException("Cannot resolve Dubbo interface name from config or URL: " + url);
    }

    private byte[] convertResult(Object result) {
        if (result == null) {
            throw new ResponseDecodeException("Dubbo invocation returned null");
        }
        if (result instanceof byte[]) {
            return ((byte[]) result);
        }
        if (result instanceof String) {
            String str = ((String) result);
            try {
                return Base64.getDecoder().decode(str);
            } catch (IllegalArgumentException e) {
                return str.getBytes(StandardCharsets.UTF_8);
            }
        }
        throw new ResponseDecodeException("Unexpected Dubbo response type: " + result.getClass().getName());
    }

    private void reconnect() {
        disconnect();
        connect();
    }

    private boolean isInterruptedFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
