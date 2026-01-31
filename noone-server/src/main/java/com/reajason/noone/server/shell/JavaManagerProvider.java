package com.reajason.noone.server.shell;

import com.reajason.noone.core.JavaManager;
import com.reajason.noone.core.client.*;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.ProfileRepository;
import com.reajason.noone.server.profile.config.HttpProtocolConfig;
import com.reajason.noone.server.profile.config.IdentifierConfig;
import com.reajason.noone.server.profile.config.IdentifierLocation;
import com.reajason.noone.server.profile.config.ProtocolConfig;
import com.reajason.noone.server.profile.config.ProtocolType;
import com.reajason.noone.server.profile.config.WebSocketProtocolConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class JavaManagerProvider {

    private static final String SIG_DELIMITER = "\n";

    @Resource
    private ProfileRepository profileRepository;

    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public JavaManager getOrCreateCached(Shell shell) {
        if (shell.getId() == null) {
            return createUncached(shell);
        }

        Profile profile = loadProfile(shell.getProfileId());
        String signature = signature(shell, profile);

        CacheEntry entry = cache.compute(shell.getId(), (id, existing) -> {
            if (existing != null && Objects.equals(existing.signature(), signature)) {
                return existing;
            }

            JavaManager next = createJavaManager(shell, profile);
            if (existing != null) {
                safeDisconnect(existing.manager());
            }
            return new CacheEntry(signature, next);
        });

        return entry.manager();
    }

    public JavaManager createUncached(Shell shell) {
        Profile profile = loadProfile(shell.getProfileId());
        return createJavaManager(shell, profile);
    }

    public void evict(Long shellId) {
        if (shellId == null) {
            return;
        }
        CacheEntry removed = cache.remove(shellId);
        if (removed != null) {
            safeDisconnect(removed.manager());
        }
    }

    private Profile loadProfile(Long profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));
    }

    private JavaManager createJavaManager(Shell shell, Profile profile) {
        ClientConfig config = buildClientConfig(shell, profile);

        Client client;
        if (profile.getProtocolType() == ProtocolType.WEBSOCKET) {
            client = new WebSocketClient(shell.getUrl(), config);
        } else {
            client = new HttpClient(shell.getUrl(), config);
        }

        return new JavaManager(client);
    }

    private ClientConfig buildClientConfig(Shell shell, Profile profile) {
        ClientConfig.ClientConfigBuilder builder = ClientConfig.builder();

        Map<String, String> requestHeaders = new HashMap<>();
        Map<String, String> requestParams = new HashMap<>();
        Map<String, String> requestCookies = new HashMap<>();

        applyProfileConfig(builder, profile, requestHeaders, requestParams, requestCookies);
        applyShellOverrides(builder, shell, requestHeaders);

        if (!requestHeaders.isEmpty()) {
            builder.requestHeaders(requestHeaders);
        }
        if (!requestParams.isEmpty()) {
            builder.requestParams(requestParams);
        }
        if (!requestCookies.isEmpty()) {
            builder.requestCookies(requestCookies);
        }

        return builder.build();
    }

    private void applyProfileConfig(
            ClientConfig.ClientConfigBuilder builder,
            Profile profile,
            Map<String, String> requestHeaders,
            Map<String, String> requestParams,
            Map<String, String> requestCookies
    ) {
        builder.transformerPassword(profile.getPassword());
        builder.requestTransformations(profile.getRequestTransformations());
        builder.responseTransformations(profile.getResponseTransformations());

        applyIdentifierConfig(profile.getIdentifier(), requestHeaders, requestParams, requestCookies);

        ProtocolConfig protocolConfig = profile.getProtocolConfig();
        if (protocolConfig == null) {
            return;
        }

        if (protocolConfig instanceof HttpProtocolConfig httpConfig) {
            builder.requestMethod(httpConfig.getRequestMethod());
            builder.requestTemplate(httpConfig.getRequestTemplate());
            builder.expectedResponseStatusCode(httpConfig.getResponseStatusCode() > 0
                    ? httpConfig.getResponseStatusCode()
                    : null);
            builder.responseTemplate(httpConfig.getResponseTemplate());
            if (httpConfig.getRequestBodyType() != null) {
                builder.requestBodyType(HttpRequestBodyType.valueOf(httpConfig.getRequestBodyType().name()));
            }
            if (httpConfig.getResponseBodyType() != null) {
                builder.responseBodyType(HttpResponseBodyType.valueOf(httpConfig.getResponseBodyType().name()));
            }
            if (httpConfig.getRequestHeaders() != null) {
                requestHeaders.putAll(httpConfig.getRequestHeaders());
            }
        } else if (protocolConfig instanceof WebSocketProtocolConfig wsConfig) {
            if (wsConfig.getHandshakeHeaders() != null) {
                requestHeaders.putAll(wsConfig.getHandshakeHeaders());
            }
        }
    }

    private void applyShellOverrides(
            ClientConfig.ClientConfigBuilder builder,
            Shell shell,
            Map<String, String> requestHeaders
    ) {
        if (shell.getCustomHeaders() != null && !shell.getCustomHeaders().isEmpty()) {
            Map<String, String> mergedHeaders = new HashMap<>(requestHeaders);
            mergedHeaders.putAll(shell.getCustomHeaders());
            requestHeaders.clear();
            requestHeaders.putAll(mergedHeaders);
        }

        if (shell.getProxyUrl() != null && !shell.getProxyUrl().isEmpty()) {
            builder.proxy(parseProxyUrl(shell.getProxyUrl()));
        }

        if (shell.getConnectTimeoutMs() != null) {
            builder.connectTimeoutMs(shell.getConnectTimeoutMs());
        }
        if (shell.getReadTimeoutMs() != null) {
            builder.readTimeoutMs(shell.getReadTimeoutMs());
        }

        if (shell.getSkipSslVerify() != null) {
            builder.skipSslVerify(shell.getSkipSslVerify());
        }

        if (shell.getMaxRetries() != null) {
            builder.maxRetries(shell.getMaxRetries());
        }
        if (shell.getRetryDelayMs() != null) {
            builder.retryDelayMs(shell.getRetryDelayMs());
        }
    }

    private void applyIdentifierConfig(
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

    private ClientConfig.ProxyConfig parseProxyUrl(String proxyUrl) {
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

            return ClientConfig.ProxyConfig.builder()
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

    private String signature(Shell shell, Profile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("url=").append(nullToEmpty(shell.getUrl())).append(SIG_DELIMITER);
        sb.append("profileId=").append(shell.getProfileId()).append(SIG_DELIMITER);
        sb.append("protocolType=").append(profile.getProtocolType()).append(SIG_DELIMITER);
        sb.append("profileUpdatedAt=").append(nullToEmpty(profile.getUpdatedAt())).append(SIG_DELIMITER);
        sb.append("proxyUrl=").append(nullToEmpty(shell.getProxyUrl())).append(SIG_DELIMITER);
        sb.append("connectTimeoutMs=").append(shell.getConnectTimeoutMs()).append(SIG_DELIMITER);
        sb.append("readTimeoutMs=").append(shell.getReadTimeoutMs()).append(SIG_DELIMITER);
        sb.append("skipSslVerify=").append(shell.getSkipSslVerify()).append(SIG_DELIMITER);
        sb.append("maxRetries=").append(shell.getMaxRetries()).append(SIG_DELIMITER);
        sb.append("retryDelayMs=").append(shell.getRetryDelayMs()).append(SIG_DELIMITER);
        sb.append("customHeaders=").append(stableHeaders(shell.getCustomHeaders())).append(SIG_DELIMITER);
        return sb.toString();
    }

    private String stableHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        return headers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void safeDisconnect(JavaManager manager) {
        try {
            manager.disconnect();
        } catch (Exception e) {
            log.debug("Failed to disconnect JavaManager", e);
        }
    }

    private record CacheEntry(String signature, JavaManager manager) {
    }
}
