package com.reajason.noone.server.shell;

import com.reajason.noone.server.shell.dto.ShellResponse;

import java.util.LinkedHashMap;
import java.util.Map;

final class ShellClientConfigCompat {

    private static final String KEY_PROXY_URL = "proxyUrl";
    private static final String KEY_CUSTOM_HEADERS = "customHeaders";
    private static final String KEY_CONNECT_TIMEOUT_MS = "connectTimeoutMs";
    private static final String KEY_READ_TIMEOUT_MS = "readTimeoutMs";
    private static final String KEY_SKIP_SSL_VERIFY = "skipSslVerify";
    private static final String KEY_MAX_RETRIES = "maxRetries";
    private static final String KEY_RETRY_DELAY_MS = "retryDelayMs";

    private ShellClientConfigCompat() {
    }

    static Map<String, Object> normalize(
            Map<String, Object> clientConfig,
            String proxyUrl,
            Map<String, String> customHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            Boolean skipSslVerify,
            Integer maxRetries,
            Long retryDelayMs
    ) {
        Map<String, Object> merged = copy(clientConfig);
        putIfNotNull(merged, "proxyUrl", proxyUrl);
        putIfNotNull(merged, "customHeaders", customHeaders == null ? null : new LinkedHashMap<>(customHeaders));
        putIfNotNull(merged, "connectTimeoutMs", connectTimeoutMs);
        putIfNotNull(merged, "readTimeoutMs", readTimeoutMs);
        putIfNotNull(merged, "skipSslVerify", skipSslVerify);
        putIfNotNull(merged, "maxRetries", maxRetries);
        putIfNotNull(merged, "retryDelayMs", retryDelayMs);
        return merged.isEmpty() ? null : merged;
    }

    static Map<String, Object> merge(
            Map<String, Object> existingClientConfig,
            Map<String, Object> requestClientConfig,
            String proxyUrl,
            Map<String, String> customHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            Boolean skipSslVerify,
            Integer maxRetries,
            Long retryDelayMs
    ) {
        return normalize(
                mergeBase(existingClientConfig, requestClientConfig),
                proxyUrl,
                customHeaders,
                connectTimeoutMs,
                readTimeoutMs,
                skipSslVerify,
                maxRetries,
                retryDelayMs
        );
    }

    static void populateLegacyFields(ShellResponse response, Map<String, Object> clientConfig) {
        response.setProxyUrl(configString(clientConfig, KEY_PROXY_URL));
        response.setCustomHeaders(configMap(clientConfig, KEY_CUSTOM_HEADERS));
        response.setConnectTimeoutMs(configInt(clientConfig, KEY_CONNECT_TIMEOUT_MS));
        response.setReadTimeoutMs(configInt(clientConfig, KEY_READ_TIMEOUT_MS));
        response.setSkipSslVerify(configBoolean(clientConfig, KEY_SKIP_SSL_VERIFY));
        response.setMaxRetries(configInt(clientConfig, KEY_MAX_RETRIES));
        response.setRetryDelayMs(configLong(clientConfig, KEY_RETRY_DELAY_MS));
    }

    static Map<String, Object> effectiveConfig(Shell shell) {
        return effectiveConfig(
                shell.getClientConfig(),
                shell.getProxyUrl(),
                shell.getCustomHeaders(),
                shell.getConnectTimeoutMs(),
                shell.getReadTimeoutMs(),
                shell.getSkipSslVerify(),
                shell.getMaxRetries(),
                shell.getRetryDelayMs()
        );
    }

    static Map<String, Object> effectiveConfig(
            Map<String, Object> clientConfig,
            String proxyUrl,
            Map<String, String> customHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            Boolean skipSslVerify,
            Integer maxRetries,
            Long retryDelayMs
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        putIfNotNull(merged, KEY_PROXY_URL, proxyUrl);
        putIfNotNull(merged, KEY_CUSTOM_HEADERS, customHeaders == null ? null : new LinkedHashMap<>(customHeaders));
        putIfNotNull(merged, KEY_CONNECT_TIMEOUT_MS, connectTimeoutMs);
        putIfNotNull(merged, KEY_READ_TIMEOUT_MS, readTimeoutMs);
        putIfNotNull(merged, KEY_SKIP_SSL_VERIFY, skipSslVerify);
        putIfNotNull(merged, KEY_MAX_RETRIES, maxRetries);
        putIfNotNull(merged, KEY_RETRY_DELAY_MS, retryDelayMs);
        if (clientConfig != null) {
            merged.putAll(copy(clientConfig));
        }
        return merged.isEmpty() ? null : merged;
    }

    static String stableConfigSignature(Map<String, Object> clientConfig) {
        if (clientConfig == null || clientConfig.isEmpty()) {
            return "";
        }
        return stableMap(clientConfig);
    }

    private static Map<String, Object> mergeBase(Map<String, Object> existing, Map<String, Object> request) {
        Map<String, Object> merged = copy(existing);
        if (request != null) {
            merged.putAll(request);
        }
        return merged;
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String stableMap(Map<?, ?> map) {
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .sorted((left, right) -> String.valueOf(left.getKey()).compareTo(String.valueOf(right.getKey())))
                .map(entry -> String.valueOf(entry.getKey()) + "=" + stableValue(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private static String stableValue(Object value) {
        if (value instanceof Map<?, ?> nestedMap) {
            return "{" + stableMap(nestedMap) + "}";
        }
        return String.valueOf(value);
    }

    private static String configString(Map<String, Object> cfg, String key) {
        if (cfg == null) {
            return null;
        }
        Object value = cfg.get(key);
        return value instanceof String s ? s : null;
    }

    private static Integer configInt(Map<String, Object> cfg, String key) {
        if (cfg == null) {
            return null;
        }
        Object value = cfg.get(key);
        return value instanceof Number n ? n.intValue() : null;
    }

    private static Long configLong(Map<String, Object> cfg, String key) {
        if (cfg == null) {
            return null;
        }
        Object value = cfg.get(key);
        return value instanceof Number n ? n.longValue() : null;
    }

    private static Boolean configBoolean(Map<String, Object> cfg, String key) {
        if (cfg == null) {
            return null;
        }
        Object value = cfg.get(key);
        return value instanceof Boolean b ? b : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> configMap(Map<String, Object> cfg, String key) {
        if (cfg == null) {
            return null;
        }
        Object value = cfg.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String keyString && entry.getValue() instanceof String valueString) {
                result.put(keyString, valueString);
            }
        }
        return result;
    }
}
