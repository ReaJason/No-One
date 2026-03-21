package com.reajason.noone.core;

import com.reajason.noone.Constants;
import com.reajason.noone.core.client.Client;
import com.reajason.noone.core.exception.*;
import com.reajason.noone.core.normalizer.CommandExecuteNormalizer;
import com.reajason.noone.core.normalizer.FileManagerNormalizer;
import com.reajason.noone.core.normalizer.PluginNormalizerRegistry;
import com.reajason.noone.server.profile.Profile;
import lombok.Data;

import java.util.*;

@Data
public abstract class ShellConnection {
    protected Client coreClient;
    protected Client loaderClient;
    protected String shellType;
    protected Profile coreProfile;
    private boolean coreInit = false;

    private final PluginCache pluginCache = new PluginCache();
    protected PluginNormalizerRegistry normalizerRegistry;

    public ShellConnection(ConnectionConfig connectionConfig) {
        this.coreClient = connectionConfig.getCoreClient();
        this.loaderClient = connectionConfig.getLoaderClient();
        this.shellType = connectionConfig.getShellType();
        this.coreProfile = connectionConfig.getCoreProfile();
        this.normalizerRegistry = new PluginNormalizerRegistry();
        this.normalizerRegistry.register("command-execute", new CommandExecuteNormalizer());
        this.normalizerRegistry.register("file-manager", new FileManagerNormalizer());
    }

    /**
     * 连接到服务器
     *
     * @return 是否连接成功
     */
    public boolean connect() {
        return coreClient.connect();
    }

    /**
     * 断开与服务器的连接
     */
    public void disconnect() {
        coreClient.disconnect();
    }

    /**
     * 检查是否已连接
     *
     * @return 是否已连接
     */
    public boolean isConnected() {
        return coreClient.isConnected();
    }

    /**
     * 发送请求到服务器（通用方法）
     *
     * @param requestMap 请求参数
     * @return 响应结果
     */
    protected Map<String, Object> sendRequest(Map<String, Object> requestMap) {
        byte[] bytes;
        try {
            bytes = TlvCodec.serialize(requestMap);
        } catch (ShellCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new RequestSerializeException("Failed to serialize shell request", e);
        }

        byte[] result;
        try {
            result = coreClient.send(bytes);
        } catch (ShellCommunicationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ShellRequestException("Failed to send shell request", false, e);
        }

        if (result == null || result.length == 0) {
            throw new ResponseDecodeException("Shell response payload is empty");
        }

        Map<String, Object> response;
        try {
            response = TlvCodec.deserialize(result);
        } catch (ShellCommunicationException e) {
            throw e;
        } catch (Exception e) {
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

    protected abstract byte[] getCoreBytes(String shellType, Profile loaderProfile);

    protected boolean loadCore(byte[] coreByes) {
        byte[] result;
        try {
            result = loaderClient.send(coreByes);
        } catch (ShellCommunicationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ShellRequestException("Failed to send shell loader request", false, e);
        }

        if (result == null || result.length == 0) {
            throw new ResponseDecodeException("Shell loader response payload is empty");
        }
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
