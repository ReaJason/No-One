package com.reajason.noone.core;

import com.reajason.noone.Constants;
import com.reajason.noone.core.client.Client;
import com.reajason.noone.core.exception.*;
import com.reajason.noone.server.profile.Profile;
import lombok.Data;

import java.lang.reflect.Array;
import java.util.*;

@Data
public abstract class ShellConnection {
    private static final String COMMAND_EXECUTE_PLUGIN = "command-execute";
    private static final String FILE_MANAGER_PLUGIN = "file-manager";
    private static final String CMD_PLACEHOLDER = "{{cmd}}";
    private static final String CWD_PLACEHOLDER = "{{cwd}}";

    protected Client coreClient;
    protected Client loaderClient;
    protected String shellType;
    protected Profile coreProfile;
    private boolean coreInit = false;

    protected Map<String, String> serverPluginCaches = new LinkedHashMap<>();
    protected boolean pluginCacheInitialized;

    public ShellConnection(ConnectionConfig connectionConfig) {
        this.coreClient = connectionConfig.getCoreClient();
        this.loaderClient = connectionConfig.getLoaderClient();
        this.shellType = connectionConfig.getShellType();
        this.coreProfile = connectionConfig.getCoreProfile();
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
        init();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.ACTION, Constants.ACTION_STATUS);
        Map<String, Object> response = sendRequest(requestMap);
        int code = requireResponseCode(response, Constants.ACTION_STATUS);
        if (code == Constants.SUCCESS) {
            serverPluginCaches = toPluginCacheMap(response.get(Constants.PLUGIN_CACHES));
            pluginCacheInitialized = true;
            return true;
        }
        if (code == Constants.FAILURE) {
            throw new ResponseBusinessException("Shell status request failed: " + errorMessage(response));
        }
        throw new ResponseDecodeException("Unexpected status response code: " + code);
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
        if (!refresh && !needLoadPlugin(pluginName)) {
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
            serverPluginCaches.put(pluginName, version);
            pluginCacheInitialized = true;
            return;
        }
        if (code == Constants.FAILURE) {
            throw new ResponseBusinessException("Load plugin failed: " + errorMessage(response));
        }
        throw new ResponseDecodeException("Unexpected load response code: " + code);
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
                caches.put(String.valueOf(entry.getKey()), stringifyPluginVersion(entry.getValue()));
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
        return !serverPluginCaches.containsKey(pluginId);
    }

    public boolean isPluginOutdated(String pluginId, String serverVersion) {
        String shellVersion = getLoadedPluginVersion(pluginId);
        if (shellVersion == null) {
            return false;
        }
        return serverVersion != null && !serverVersion.equals(shellVersion);
    }

    public String getLoadedPluginVersion(String pluginId) {
        if (!serverPluginCaches.containsKey(pluginId)) {
            return null;
        }
        return serverPluginCaches.get(pluginId);
    }

    private String stringifyPluginVersion(Object value) {
        if (value == null) {
            return null;
        }
        String version = String.valueOf(value).trim();
        return version.isEmpty() ? null : version;
    }

    public abstract void fillLoadPluginRequestMaps(String pluginName, byte[] pluginCodeBytes, Map<String, Object> requestMap);

    public Map<String, Object> runPlugin(String pluginName, Map<String, Object> args) {
        Map<String, Object> pluginArgs = args;
        if (COMMAND_EXECUTE_PLUGIN.equals(pluginName)) {
            pluginArgs = normalizeCommandExecuteArgs(args);
            if (isLocalFailure(pluginArgs)) {
                return pluginArgs;
            }
        } else if (FILE_MANAGER_PLUGIN.equals(pluginName)) {
            pluginArgs = normalizeFileManagerArgs(args);
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
        if (FILE_MANAGER_PLUGIN.equals(pluginName)) {
            return normalizeFileManagerResponse(response);
        }
        return response;
    }

    private Map<String, Object> normalizeCommandExecuteArgs(Map<String, Object> args) {
        String cmd = asTrimString(args != null ? args.get("cmd") : null);
        if (cmd == null || cmd.isEmpty()) {
            return localFailure("cmd is required");
        }

        String cwd = asTrimString(args.get("cwd"));
        String charset = asTrimString(args.get("charset"));
        if (charset == null || charset.isEmpty()) {
            charset = "UTF-8";
        }

        String cdTarget = parseCdTarget(cmd);
        Map<String, Object> normalized = new HashMap<>();
        normalized.put("cwd", cwd == null ? "" : cwd);
        normalized.put("charset", charset);
        if (cdTarget != null) {
            normalized.put("op", "cd");
            normalized.put("cdTarget", cdTarget);
            return normalized;
        }

        Map<String, Object> template = toStringObjectMap(args.get("commandTemplate"));
        String executable = template == null ? null : renderTemplate(asTrimString(template.get("executable")), cmd, cwd);
        if (executable == null || executable.isEmpty()) {
            return localFailure("commandTemplate.executable is required");
        }
        normalized.put("op", "exec");
        normalized.put("executable", executable);
        normalized.put("argv", parseTemplateArgs(template.get("args"), cmd, cwd));
        normalized.put("env", parseTemplateEnv(template.get("env"), cmd, cwd));
        return normalized;
    }

    private Map<String, Object> normalizeFileManagerArgs(Map<String, Object> args) {
        try {
            Map<String, Object> source = args == null ? new HashMap<>() : args;
            return normalizeFileManagerMap(source);
        } catch (IllegalArgumentException e) {
            return localFailure("file-manager args normalization failed: " + e.getMessage());
        }
    }

    private Map<String, Object> normalizeFileManagerMap(Map<String, Object> source) {
        Map<String, Object> normalized = new HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey();
            normalized.put(key, normalizeFileManagerValue(key, entry.getValue()));
        }
        return normalized;
    }

    private Object normalizeFileManagerValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if ("bytes".equals(key)) {
            return toByteArray(value);
        }
        if (value instanceof Map<?, ?> mapValue) {
            return normalizeFileManagerMap(toStringObjectMap(mapValue));
        }
        if (value instanceof Iterable<?>) {
            List<Object> list = new ArrayList<>();
            for (Object item : (Iterable<?>) value) {
                list.add(normalizeFileManagerValue(null, item));
            }
            return list;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(normalizeFileManagerValue(null, Array.get(value, i)));
            }
            return list;
        }
        return value;
    }

    private byte[] toByteArray(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof byte[] bytes) {
            return bytes;
        }
        if (raw instanceof Iterable<?> iterable) {
            List<Byte> bytes = new ArrayList<>();
            for (Object item : iterable) {
                bytes.add(toSingleByte(item));
            }
            byte[] out = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) {
                out[i] = bytes.get(i);
            }
            return out;
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            byte[] out = new byte[length];
            for (int i = 0; i < length; i++) {
                out[i] = toSingleByte(Array.get(raw, i));
            }
            return out;
        }
        throw new IllegalArgumentException("unsupported bytes value type: " + raw.getClass().getName());
    }

    private byte toSingleByte(Object raw) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("bytes element is not a number: " + raw);
        }
        int value = number.intValue();
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("bytes element out of range [0,255]: " + value);
        }
        return (byte) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeFileManagerResponse(Map<String, Object> response) {
        Object converted = convertBinaryToJsonSafeValue(response);
        if (converted instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    private Object convertBinaryToJsonSafeValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof byte[] bytes) {
            List<Integer> numbers = new ArrayList<>(bytes.length);
            for (byte b : bytes) {
                numbers.add(b & 0xff);
            }
            return numbers;
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> copied = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                copied.put(String.valueOf(entry.getKey()), convertBinaryToJsonSafeValue(entry.getValue()));
            }
            return copied;
        }
        if (raw instanceof Iterable<?> iterable) {
            List<Object> copied = new ArrayList<>();
            for (Object item : iterable) {
                copied.add(convertBinaryToJsonSafeValue(item));
            }
            return copied;
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            List<Object> copied = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                copied.add(convertBinaryToJsonSafeValue(Array.get(raw, i)));
            }
            return copied;
        }
        return raw;
    }

    private boolean isLocalFailure(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        Object code = response.get(Constants.CODE);
        return code instanceof Number && ((Number) code).intValue() == Constants.FAILURE;
    }

    private Map<String, Object> localFailure(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.CODE, Constants.FAILURE);
        response.put(Constants.ERROR, message);
        return response;
    }

    private Map<String, Object> toStringObjectMap(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> copied = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            copied.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copied;
    }

    private List<String> parseTemplateArgs(Object rawArgs, String cmd, String cwd) {
        List<String> args = new ArrayList<>();
        if (rawArgs == null) {
            return args;
        }
        if (rawArgs instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) rawArgs) {
                if (item != null) {
                    args.add(renderTemplate(String.valueOf(item), cmd, cwd));
                }
            }
            return args;
        }
        Class<?> rawArgsClass = rawArgs.getClass();
        if (rawArgsClass.isArray()) {
            int length = Array.getLength(rawArgs);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(rawArgs, i);
                if (item != null) {
                    args.add(renderTemplate(String.valueOf(item), cmd, cwd));
                }
            }
            return args;
        }
        String single = asTrimString(rawArgs);
        if (single != null && !single.isEmpty()) {
            args.add(renderTemplate(single, cmd, cwd));
        }
        return args;
    }

    private Map<String, String> parseTemplateEnv(Object rawEnv, String cmd, String cwd) {
        Map<String, String> env = new HashMap<>();
        if (!(rawEnv instanceof Map<?, ?> map)) {
            return env;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = asTrimString(entry.getKey());
            if (key == null || key.isEmpty()) {
                continue;
            }
            env.put(key, renderTemplate(String.valueOf(entry.getValue()), cmd, cwd));
        }
        return env;
    }

    private String parseCdTarget(String cmd) {
        if (cmd == null) {
            return null;
        }
        String trimmed = cmd.trim();
        if (!trimmed.startsWith("cd")) {
            return null;
        }
        if (trimmed.length() > 2 && !Character.isWhitespace(trimmed.charAt(2))) {
            return null;
        }
        String rawTarget = trimmed.substring(2).trim();
        if (rawTarget.contains("&&") || rawTarget.contains("||") || rawTarget.contains(";") || rawTarget.contains("|")) {
            return null;
        }
        if (rawTarget.isEmpty()) {
            return "~";
        }
        return rawTarget;
    }

    private String renderTemplate(String template, String cmd, String cwd) {
        if (template == null) {
            return null;
        }
        String rendered = template.replace(CMD_PLACEHOLDER, cmd);
        return rendered.replace(CWD_PLACEHOLDER, cwd == null ? "" : cwd);
    }

    private String asTrimString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
