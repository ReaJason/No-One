package com.reajason.noone.core.client;

import com.reajason.noone.core.exception.RequestInterruptedException;
import com.reajason.noone.core.exception.RequestSendException;
import com.reajason.noone.core.exception.RequestSerializeException;
import com.reajason.noone.core.exception.ResponseDecodeException;
import com.reajason.noone.core.transform.TrafficTransformer;
import com.reajason.noone.core.transform.TransformationSpec;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.service.GenericService;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Dubbo RPC client using GenericService for protocol-agnostic invocation.
 * Supports dubbo://, hessian://, and tri:// sub-protocols via direct connection.
 *
 * @author ReaJason
 */
@Slf4j
@NoArgsConstructor
public class DubboClient implements Client {

    @Setter
    @Getter
    private String url;

    @Setter
    @Getter
    private ClientConfig config;

    @Setter
    @Getter
    private DubboClientConfig dubboConfig;

    private volatile ReferenceConfig<GenericService> referenceConfig;
    private volatile GenericService genericService;
    private volatile boolean connected;
    private final Object sendLock = new Object();

    public DubboClient(String url, ClientConfig config, DubboClientConfig dubboConfig) {
        this.url = url;
        this.config = config != null ? config : ClientConfig.builder().build();
        this.dubboConfig = dubboConfig != null ? dubboConfig : DubboClientConfig.builder().build();
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
        if (!connected || genericService == null) {
            connect();
        }

        byte[] transformedPayload;
        try {
            TransformationSpec requestSpec = TransformationSpec.parse(config.getRequestTransformations());
            transformedPayload = TrafficTransformer.outbound(
                    payload, requestSpec, config.getTransformerPassword()
            );
        } catch (RuntimeException e) {
            throw new RequestSerializeException("Failed to prepare Dubbo request payload", e);
        }

        Object result;
        try {
            result = genericService.$invoke(
                    dubboConfig.getMethodName(),
                    dubboConfig.getParameterTypes(),
                    new Object[]{transformedPayload}
            );
        } catch (Exception e) {
            if (isInterruptedFailure(e)) {
                Thread.currentThread().interrupt();
                throw new RequestInterruptedException("Dubbo invocation was interrupted", e);
            }
            if (allowReconnect) {
                log.debug("Dubbo invocation failed, attempting reconnect", e);
                reconnect();
                return doSend(payload, false);
            }
            throw new RequestSendException("Dubbo invocation failed after reconnect: " + e.getMessage(), 2, e);
        }

        byte[] responseBytes = convertResult(result);

        TransformationSpec responseSpec = TransformationSpec.parse(config.getResponseTransformations());
        byte[] inbound;
        try {
            inbound = TrafficTransformer.inbound(
                    responseBytes, responseSpec, config.getTransformerPassword()
            );
        } catch (RuntimeException e) {
            throw new ResponseDecodeException("Failed to decode Dubbo response payload", e);
        }
        if (inbound == null) {
            throw new ResponseDecodeException("Decoded Dubbo response payload is null");
        }
        return inbound;
    }

    /**
     * Resolves the Dubbo interface name: prefers explicit config, falls back to parsing the URL path.
     */
    private String resolveInterfaceName() {
        if (dubboConfig.getInterfaceName() != null && !dubboConfig.getInterfaceName().isEmpty()) {
            return dubboConfig.getInterfaceName();
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
        if (result instanceof byte[] bytes) {
            return bytes;
        }
        if (result instanceof String str) {
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
