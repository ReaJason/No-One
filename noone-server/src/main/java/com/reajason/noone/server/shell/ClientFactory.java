package com.reajason.noone.server.shell;

import com.reajason.noone.core.client.*;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.core.profile.config.*;
import com.reajason.noone.core.transform.TransformConfig;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ClientFactory {

    public static Client create(Shell shell, Profile profile) {
        if (profile.getProtocolType() == ProtocolType.HTTP) {
            TransformConfig tc = TransformConfig.fromProfile(profile);
            HttpClientConfig config = buildHttpClientConfig(shell, profile, tc);
            return new HttpClient(shell.getUrl(), config);
        } else if (profile.getProtocolType() == ProtocolType.WEBSOCKET) {
            WebSocketClientConfig config = buildWebSocketClientConfig(shell, profile);
            return new WebSocketClient(shell.getUrl(), config);
        } else if (profile.getProtocolType() == ProtocolType.DUBBO) {
            DubboClientConfig config = buildDubboClientConfig(shell, profile);
            ProtocolConfig protocolConfig = profile.getProtocolConfig();
            boolean useAlibaba = protocolConfig instanceof DubboProtocolConfig dubboProtoConfig
                    && "ALIBABA".equalsIgnoreCase(dubboProtoConfig.getDubboStack());
            if (useAlibaba) {
                return new AlibabaDubboClient(shell.getUrl(), config);
            }
            return new ApacheDubboClient(shell.getUrl(), config);
        }
        throw new IllegalArgumentException("Unsupported protocol type: " + profile.getProtocolType());
    }

    private static HttpClientConfig buildHttpClientConfig(Shell shell, Profile profile, TransformConfig tc) {
        HttpClientConfig.HttpClientConfigBuilder builder = HttpClientConfig.builder();
        Map<String, String> requestHeaders = new HashMap<>();
        Map<String, String> requestParams = new HashMap<>();
        Map<String, String> requestCookies = new HashMap<>();

        applyIdentifierConfig(profile.getIdentifier(), requestHeaders, requestParams, requestCookies);

        ProtocolConfig protocolConfig = profile.getProtocolConfig();
        if (protocolConfig instanceof HttpProtocolConfig httpConfig) {
            builder.requestMethod(httpConfig.getRequestMethod());
            builder.expectedResponseStatusCode(httpConfig.getResponseStatusCode() > 0
                    ? httpConfig.getResponseStatusCode()
                    : null);
            if (tc.contentType() != null) {
                builder.contentType(tc.contentType());
            }
            if (httpConfig.getRequestHeaders() != null) {
                requestHeaders.putAll(httpConfig.getRequestHeaders());
            }
        }

        Map<String, Object> cfg = safeConfig(shell);

        // Apply custom headers from clientConfig
        Map<String, String> customHeaders = configMap(cfg, "customHeaders");
        if (customHeaders != null && !customHeaders.isEmpty()) {
            requestHeaders.putAll(customHeaders);
        }

        if (!requestHeaders.isEmpty()) {
            builder.requestHeaders(requestHeaders);
        }
        if (!requestParams.isEmpty()) {
            builder.requestParams(requestParams);
        }
        if (!requestCookies.isEmpty()) {
            builder.requestCookies(requestCookies);
        }

        // Apply connection overrides from clientConfig
        String proxyUrl = configString(cfg, "proxyUrl");
        if (proxyUrl != null && !proxyUrl.isEmpty()) {
            builder.proxy(parseProxyUrl(proxyUrl));
        }
        Integer connectTimeoutMs = configInt(cfg, "connectTimeoutMs");
        if (connectTimeoutMs != null) {
            builder.connectTimeoutMs(connectTimeoutMs);
        }
        Integer readTimeoutMs = configInt(cfg, "readTimeoutMs");
        if (readTimeoutMs != null) {
            builder.readTimeoutMs(readTimeoutMs);
        }
        Boolean skipSslVerify = configBoolean(cfg, "skipSslVerify");
        if (skipSslVerify != null) {
            builder.skipSslVerify(skipSslVerify);
        }
        Integer maxRetries = configInt(cfg, "maxRetries");
        if (maxRetries != null) {
            builder.maxRetries(maxRetries);
        }
        Long retryDelayMs = configLong(cfg, "retryDelayMs");
        if (retryDelayMs != null) {
            builder.retryDelayMs(retryDelayMs);
        }

        return builder.build();
    }

    private static WebSocketClientConfig buildWebSocketClientConfig(Shell shell, Profile profile) {
        WebSocketClientConfig.WebSocketClientConfigBuilder builder = WebSocketClientConfig.builder();
        Map<String, String> requestHeaders = new HashMap<>();
        Map<String, String> requestParams = new HashMap<>();
        Map<String, String> requestCookies = new HashMap<>();

        applyIdentifierConfig(profile.getIdentifier(), requestHeaders, requestParams, requestCookies);

        ProtocolConfig protocolConfig = profile.getProtocolConfig();
        if (protocolConfig instanceof WebSocketProtocolConfig wsConfig) {
            if (wsConfig.getHandshakeHeaders() != null) {
                requestHeaders.putAll(wsConfig.getHandshakeHeaders());
            }
        }

        Map<String, Object> cfg = safeConfig(shell);

        Map<String, String> customHeaders = configMap(cfg, "customHeaders");
        if (customHeaders != null && !customHeaders.isEmpty()) {
            requestHeaders.putAll(customHeaders);
        }

        if (!requestHeaders.isEmpty()) {
            builder.requestHeaders(requestHeaders);
        }

        String proxyUrl = configString(cfg, "proxyUrl");
        if (proxyUrl != null && !proxyUrl.isEmpty()) {
            builder.proxy(parseProxyUrl(proxyUrl));
        }
        Integer connectTimeoutMs = configInt(cfg, "connectTimeoutMs");
        if (connectTimeoutMs != null) {
            builder.connectTimeoutMs(connectTimeoutMs);
        }
        Integer readTimeoutMs = configInt(cfg, "readTimeoutMs");
        if (readTimeoutMs != null) {
            builder.readTimeoutMs(readTimeoutMs);
        }
        Boolean skipSslVerify = configBoolean(cfg, "skipSslVerify");
        if (skipSslVerify != null) {
            builder.skipSslVerify(skipSslVerify);
        }

        return builder.build();
    }

    private static DubboClientConfig buildDubboClientConfig(Shell shell, Profile profile) {
        DubboClientConfig.DubboClientConfigBuilder builder = DubboClientConfig.builder();
        builder.interfaceName(shell.getInterfaceName());
        ProtocolConfig protocolConfig = profile.getProtocolConfig();
        if (protocolConfig instanceof DubboProtocolConfig dubboProtoConfig) {
            if (dubboProtoConfig.getMethodName() != null && !dubboProtoConfig.getMethodName().isEmpty()) {
                builder.methodName(dubboProtoConfig.getMethodName());
            }
            if (dubboProtoConfig.getParameterTypes() != null && dubboProtoConfig.getParameterTypes().length > 0) {
                builder.parameterTypes(dubboProtoConfig.getParameterTypes());
            }
        }
        Map<String, Object> cfg = safeConfig(shell);
        Integer readTimeoutMs = configInt(cfg, "readTimeoutMs");
        if (readTimeoutMs != null) {
            builder.readTimeoutMs(readTimeoutMs);
        }
        String proxyUrl = configString(cfg, "proxyUrl");
        if (proxyUrl != null && !proxyUrl.isEmpty()) {
            ProxyConfig proxyConfig = parseProxyUrl(proxyUrl);
            if (proxyConfig != null) {
                if ("SOCKS4".equalsIgnoreCase(proxyConfig.getType())) {
                    throw new IllegalArgumentException("SOCKS4 proxy is not supported for Dubbo clients. Use SOCKS5 instead.");
                }
                builder.proxy(proxyConfig);
            }
        }
        return builder.build();
    }

    private static void applyIdentifierConfig(
            IdentifierConfig identifier,
            Map<String, String> requestHeaders,
            Map<String, String> requestParams,
            Map<String, String> requestCookies
    ) {
        if (identifier == null) {
            return;
        }
        if (identifier.getLocation() == null || identifier.getName() == null || identifier.getValue() == null) {
            return;
        }

        IdentifierLocation location = identifier.getLocation();
        switch (location) {
            case HEADER, METADATA -> requestHeaders.put(identifier.getName(), identifier.getValue());
            case QUERY_PARAM -> requestParams.put(identifier.getName(), identifier.getValue());
            case COOKIE -> requestCookies.put(identifier.getName(), identifier.getValue());
        }
    }

    static ProxyConfig parseProxyUrl(String proxyUrl) {
        try {
            URI uri = new URI(proxyUrl);
            String type = uri.getScheme().toUpperCase();
            String host = uri.getHost();
            int port = uri.getPort();
            String username = null;
            String password = null;

            if (uri.getUserInfo() != null) {
                String[] userInfo = uri.getUserInfo().split(":", 2);
                username = userInfo[0];
                if (userInfo.length > 1) {
                    password = userInfo[1];
                }
            }

            return ProxyConfig.builder()
                    .type(type)
                    .host(host)
                    .port(port)
                    .username(username)
                    .password(password)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse proxy URL: {}", proxyUrl, e);
            return null;
        }
    }

    // ==================== clientConfig helper methods ====================

    private static Map<String, Object> safeConfig(Shell shell) {
        Map<String, Object> cfg = ShellClientConfigCompat.effectiveConfig(shell);
        return cfg != null ? cfg : Map.of();
    }

    private static String configString(Map<String, Object> cfg, String key) {
        Object v = cfg.get(key);
        return v instanceof String s ? s : null;
    }

    private static Integer configInt(Map<String, Object> cfg, String key) {
        Object v = cfg.get(key);
        return v instanceof Number n ? n.intValue() : null;
    }

    private static Long configLong(Map<String, Object> cfg, String key) {
        Object v = cfg.get(key);
        return v instanceof Number n ? n.longValue() : null;
    }

    private static Boolean configBoolean(Map<String, Object> cfg, String key) {
        Object v = cfg.get(key);
        return v instanceof Boolean b ? b : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> configMap(Map<String, Object> cfg, String key) {
        Object v = cfg.get(key);
        return v instanceof Map ? (Map<String, String>) v : null;
    }
}
