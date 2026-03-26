package com.reajason.noone.core;

import com.reajason.noone.core.client.Client;
import com.reajason.noone.core.client.ResponseDecodeException;
import com.reajason.noone.core.client.ShellCommunicationException;
import com.reajason.noone.core.client.ShellRequestException;
import com.reajason.noone.core.exception.RequestSerializeException;
import com.reajason.noone.core.exception.ResponseBusinessException;
import com.reajason.noone.core.normalizer.CommandExecuteNormalizer;
import com.reajason.noone.core.normalizer.FileManagerNormalizer;
import com.reajason.noone.core.normalizer.PluginNormalizerRegistry;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.core.profile.config.HttpBodyTemplateEngine;
import com.reajason.noone.core.profile.config.HttpRequestBodyType;
import com.reajason.noone.core.profile.config.HttpResponseBodyType;
import com.reajason.noone.core.transform.TrafficTransformer;
import com.reajason.noone.core.transform.TransformConfig;
import lombok.Data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public abstract class ShellConnection {
    protected Client coreClient;
    protected TransformConfig coreTransform;
    protected Client loaderClient;
    protected TransformConfig loaderTransform;
    protected String shellType;
    protected Profile coreProfile;
    private boolean coreInit = false;

    private final PluginCache pluginCache = new PluginCache();
    protected PluginNormalizerRegistry normalizerRegistry;

    public ShellConnection(Client coreClient, Profile coreProfile) {
        this.coreClient = coreClient;
        this.coreProfile = coreProfile;
        this.coreTransform = TransformConfig.fromProfile(coreProfile);
        this.normalizerRegistry = new PluginNormalizerRegistry();
        this.normalizerRegistry.register("command-execute", new CommandExecuteNormalizer());
        this.normalizerRegistry.register("file-manager", new FileManagerNormalizer());
    }

    public ShellConnection(Client coreClient, Profile coreProfile,
                           Client loaderClient, Profile loaderProfile, String shellType) {
        this(coreClient, coreProfile);
        this.loaderClient = loaderClient;
        this.loaderTransform = TransformConfig.fromProfile(loaderProfile);
        this.shellType = shellType;
    }

    public void disconnect() {
        if (coreClient != null) {
            coreClient.disconnect();
        }
        if (loaderClient != null) {
            loaderClient.disconnect();
        }
    }

    protected byte[] transformAndSend(Client client, TransformConfig tc, byte[] payload) {
        byte[] outbound;
        try {
            outbound = TrafficTransformer.outbound(payload, tc.requestSpec(), tc.password());
        } catch (RuntimeException e) {
            throw new RequestSerializeException("Failed to transform outbound payload", e);
        }

        byte[] encoded;
        try {
            encoded = encodePayload(tc, outbound);
        } catch (RuntimeException e) {
            throw new RequestSerializeException("Failed to encode request payload", e);
        }

        byte[] response;
        try {
            response = client.send(encoded);
        } catch (RuntimeException e) {
            if (e instanceof ShellCommunicationException) throw e;
            throw new ShellRequestException("Failed to send request", false, e);
        }

        if (response == null || response.length == 0) {
            throw new ResponseDecodeException("Response payload is empty");
        }

        byte[] extracted;
        try {
            extracted = decodePayload(tc, response);
        } catch (RuntimeException e) {
            if (e instanceof ShellCommunicationException) throw e;
            throw new ResponseDecodeException("Failed to extract response payload", e);
        }

        try {
            byte[] inbound = TrafficTransformer.inbound(extracted, tc.responseSpec(), tc.password());
            if (inbound == null) {
                throw new ResponseDecodeException("Decoded response payload is null");
            }
            return inbound;
        } catch (RuntimeException e) {
            if (e instanceof ShellCommunicationException) throw e;
            throw new ResponseDecodeException("Failed to transform inbound payload", e);
        }
    }

    private byte[] encodePayload(TransformConfig tc, byte[] payload) {
        if (tc.requestBodyType() == null && tc.requestTemplate() == null) {
            return payload;
        }
        HttpRequestBodyType bodyType = tc.requestBodyType() != null ? tc.requestBodyType() : HttpRequestBodyType.BINARY;
        return HttpBodyTemplateEngine.encodeRequestBody(bodyType, tc.requestTemplate(), payload).bytes();
    }

    private byte[] decodePayload(TransformConfig tc, byte[] response) {
        if (tc.responseBodyType() == null && tc.responseTemplate() == null) {
            return response;
        }
        HttpResponseBodyType bodyType = tc.responseBodyType() != null ? tc.responseBodyType() : HttpResponseBodyType.BINARY;
        byte[] extracted = HttpBodyTemplateEngine.extractResponsePayloadBytes(bodyType, tc.responseTemplate(), response);
        if (extracted == null) {
            throw new ResponseDecodeException("Failed to extract payload from response body");
        }
        return extracted;
    }

    protected Map<String, Object> sendRequest(Map<String, Object> requestMap) {
        byte[] bytes;
        try {
            bytes = TlvCodec.serialize(requestMap);
        } catch (Exception e) {
            if (e instanceof ShellCommunicationException) throw (ShellCommunicationException) e;
            throw new RequestSerializeException("Failed to serialize shell request", e);
        }

        byte[] result = transformAndSend(coreClient, coreTransform, bytes);

        Map<String, Object> response;
        try {
            response = TlvCodec.deserialize(result);
        } catch (Exception e) {
            if (e instanceof ShellCommunicationException) throw (ShellCommunicationException) e;
            throw new ResponseDecodeException("Failed to deserialize shell response", e);
        }
        return response;
    }

    public boolean init() {
        if (loaderClient != null) {
            byte[] coreBytes = getCoreBytes(shellType, coreProfile);
            return loadCore(coreBytes);
        }
        return true;
    }

    protected abstract byte[] getCoreBytes(String shellType, Profile coreProfile);

    protected boolean loadCore(byte[] coreBytes) {
        byte[] result = transformAndSend(loaderClient, loaderTransform, coreBytes);
        coreInit = new String(result).equals("ok");
        return coreInit;
    }

    public boolean test() {
        return checkStatus();
    }

    public boolean checkStatus() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.ACTION, Constants.ACTION_STATUS);
        Map<String, Object> response = sendRequest(requestMap);
        int code = requireResponseCode(response, Constants.ACTION_STATUS);
        if (code == Constants.SUCCESS) {
            pluginCache.initialize(toPluginCacheMap(response.get(Constants.PLUGIN_CACHES)));
            return true;
        }
        throw new ResponseBusinessException("Shell status request failed: " + errorMessage(response));
    }

    public void loadPlugin(String pluginName, byte[] pluginCodeBytes) {
        loadPluginInternal(pluginName, null, pluginCodeBytes, false);
    }

    public void loadPlugin(String pluginName, String version, byte[] pluginCodeBytes) {
        loadPluginInternal(pluginName, version, pluginCodeBytes, false);
    }

    public void refreshPlugin(String pluginName, String version, byte[] pluginCodeBytes) {
        loadPluginInternal(pluginName, version, pluginCodeBytes, true);
    }

    private void loadPluginInternal(String pluginName, String version, byte[] pluginCodeBytes, boolean refresh) {
        if (!refresh && !pluginCache.needLoad(pluginName)) {
            return;
        }
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.ACTION, Constants.ACTION_LOAD);
        requestMap.put(Constants.PLUGIN, pluginName);
        if (version != null) {
            requestMap.put(Constants.VERSION, version);
        }
        requestMap.put(Constants.REFRESH, String.valueOf(refresh));
        fillLoadPluginRequestMaps(pluginName, pluginCodeBytes, requestMap);
        Map<String, Object> response = sendRequest(requestMap);
        int code = requireResponseCode(response, Constants.ACTION_LOAD);
        if (code == Constants.SUCCESS) {
            pluginCache.put(pluginName, version);
            return;
        }
        throw new ResponseBusinessException("Load plugin failed: " + errorMessage(response));
    }

    private int requireResponseCode(Map<String, Object> response, String action) {
        Object codeObj = response.get(Constants.CODE);
        if (!(codeObj instanceof Number code)) {
            throw new ResponseDecodeException("Missing or invalid code in '" + action + "' response");
        }
        return code.intValue();
    }

    private String errorMessage(Map<String, Object> response) {
        Object error = response.get(Constants.ERROR);
        if (error == null) {
            return "unknown error";
        }
        String message = String.valueOf(error);
        return message.isBlank() ? "unknown error" : message;
    }

    private Map<String, String> toPluginCacheMap(Object pluginCachesObj) {
        Map<String, String> caches = new LinkedHashMap<>();
        if (pluginCachesObj instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String version = entry.getValue() == null ? null : String.valueOf(entry.getValue()).trim();
                caches.put(String.valueOf(entry.getKey()), (version == null || version.isEmpty()) ? null : version);
            }
            return caches;
        }
        if (!(pluginCachesObj instanceof Iterable<?> rawSet)) {
            return caches;
        }
        for (Object item : rawSet) {
            if (item != null) {
                caches.put(String.valueOf(item), null);
            }
        }
        return caches;
    }

    public boolean needLoadPlugin(String pluginId) {
        return pluginCache.needLoad(pluginId);
    }

    public boolean isPluginOutdated(String pluginId, String serverVersion) {
        return pluginCache.isOutdated(pluginId, serverVersion);
    }

    public String getLoadedPluginVersion(String pluginId) {
        return pluginCache.getVersion(pluginId);
    }

    public boolean isPluginCacheInitialized() {
        return pluginCache.isInitialized();
    }

    public abstract void fillLoadPluginRequestMaps(String pluginName, byte[] pluginCodeBytes, Map<String, Object> requestMap);

    public Map<String, Object> runPlugin(String pluginName, Map<String, Object> args) {
        Map<String, Object> pluginArgs = args;
        var normalizer = normalizerRegistry.find(pluginName);
        if (normalizer.isPresent()) {
            pluginArgs = normalizer.get().normalizeArgs(args);
            if (isLocalFailure(pluginArgs)) {
                return pluginArgs;
            }
        }

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.ACTION, Constants.ACTION_RUN);
        requestMap.put(Constants.PLUGIN, pluginName);
        if (pluginArgs != null) {
            requestMap.put(Constants.ARGS, pluginArgs);
        }
        Map<String, Object> response = sendRequest(requestMap);
        return normalizer.map(n -> n.normalizeResponse(response)).orElse(response);
    }

    private boolean isLocalFailure(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        Object code = response.get(Constants.CODE);
        return code instanceof Number && ((Number) code).intValue() == Constants.FAILURE;
    }
}
