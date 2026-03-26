package com.reajason.noone.server.shell;

import com.reajason.noone.core.DotNetConnection;
import com.reajason.noone.core.JavaConnection;
import com.reajason.noone.core.NodeJsConnection;
import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.client.*;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.core.profile.config.*;
import com.reajason.noone.core.transform.TransformConfig;
import com.reajason.noone.server.profile.ProfileEntity;
import com.reajason.noone.server.profile.ProfileMapper;
import com.reajason.noone.server.profile.ProfileRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ShellConnectionPool {

    private static final String SIG_DELIMITER = "\n";

    @Resource
    private ProfileRepository profileRepository;

    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    private final ProfileMapper profileMapper;

    public ShellConnectionPool(ProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    public ShellConnection getOrCreateCached(Shell shell) {
        if (shell.getId() == null) {
            return createUncached(shell);
        }

        ProfileEntity profile = loadProfile(shell.getProfileId());
        ProfileEntity loaderProfile;
        if (shell.getStaging()) {
            loaderProfile = loadProfile(shell.getLoaderProfileId());
        } else {
            loaderProfile = null;
        }

        String signature = signature(shell, profile, loaderProfile);

        CacheEntry entry = cache.compute(shell.getId(), (id, existing) -> {
            if (existing != null && Objects.equals(existing.signature(), signature)) {
                return existing;
            }

            ShellConnection next = createConnection(shell, profile, loaderProfile);
            if (existing != null) {
                safeDisconnect(existing.connection());
            }
            return new CacheEntry(signature, next);
        });

        return entry.connection();
    }

    public ShellConnection createUncached(Shell shell) {
        ProfileEntity profile = loadProfile(shell.getProfileId());
        ProfileEntity loaderProfile = null;
        if (shell.getStaging()) {
            loaderProfile = loadProfile(shell.getLoaderProfileId());
        }
        return createConnection(shell, profile, loaderProfile);
    }

    public void evict(Long shellId) {
        if (shellId == null) {
            return;
        }
        CacheEntry removed = cache.remove(shellId);
        if (removed != null) {
            safeDisconnect(removed.connection());
        }
    }

    private ProfileEntity loadProfile(Long profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));
    }

    private ShellConnection createConnection(Shell shell, ProfileEntity profile, ProfileEntity loaderProfile) {
        Profile coreProfile = profileMapper.toProfile(profile);
        Client coreClient = buildClient(shell, profile, coreProfile);

        ShellLanguage language = effectiveLanguage(shell);

        ShellConnection conn;
        if (shell.getStaging()) {
            Profile loaderProfileMapped = profileMapper.toProfile(loaderProfile);
            Client loaderClient = buildClient(shell, loaderProfile, loaderProfileMapped);

            conn = switch (language) {
                case JAVA -> new JavaConnection(coreClient, coreProfile, loaderClient, loaderProfileMapped, shell.getShellType());
                case NODEJS -> new NodeJsConnection(coreClient, coreProfile, loaderClient, loaderProfileMapped, shell.getShellType());
                case DOTNET -> new DotNetConnection(coreClient, coreProfile, loaderClient, loaderProfileMapped, shell.getShellType());
            };
        } else {
            conn = switch (language) {
                case JAVA -> new JavaConnection(coreClient, coreProfile);
                case NODEJS -> new NodeJsConnection(coreClient, coreProfile);
                case DOTNET -> new DotNetConnection(coreClient, coreProfile);
            };
        }
        return conn;
    }

    private Client buildClient(Shell shell, ProfileEntity profile, Profile coreProfile) {
        if (profile.getProtocolType() == ProtocolType.HTTP) {
            TransformConfig tc = TransformConfig.fromProfile(coreProfile);
            HttpClientConfig config = buildHttpClientConfig(shell, profile, tc);
            return new HttpClient(shell.getUrl(), config);
        } else if (profile.getProtocolType() == ProtocolType.WEBSOCKET) {
            WebSocketClientConfig config = buildWebSocketClientConfig(shell, profile);
            return new WebSocketClient(shell.getUrl(), config);
        } else if (profile.getProtocolType() == ProtocolType.DUBBO) {
            DubboClientConfig config = buildDubboClientConfig(shell, profile);
            return new DubboClient(shell.getUrl(), config);
        }
        throw new IllegalArgumentException("Unsupported protocol type: " + profile.getProtocolType());
    }

    private ShellLanguage effectiveLanguage(Shell shell) {
        return shell.getLanguage() != null ? shell.getLanguage() : ShellLanguage.JAVA;
    }

    private HttpClientConfig buildHttpClientConfig(Shell shell, ProfileEntity profile, TransformConfig tc) {
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

    private WebSocketClientConfig buildWebSocketClientConfig(Shell shell, ProfileEntity profile) {
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

        if (shell.getCustomHeaders() != null && !shell.getCustomHeaders().isEmpty()) {
            requestHeaders.putAll(shell.getCustomHeaders());
        }

        if (!requestHeaders.isEmpty()) {
            builder.requestHeaders(requestHeaders);
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

        return builder.build();
    }

    private DubboClientConfig buildDubboClientConfig(Shell shell, ProfileEntity profile) {
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
        if (shell.getReadTimeoutMs() != null) {
            builder.readTimeoutMs(shell.getReadTimeoutMs());
        }
        return builder.build();
    }

    private void applyShellOverrides(
            HttpClientConfig.HttpClientConfigBuilder builder,
            Shell shell,
            Map<String, String> requestHeaders
    ) {
        if (shell.getCustomHeaders() != null && !shell.getCustomHeaders().isEmpty()) {
            requestHeaders.putAll(shell.getCustomHeaders());
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

    private ProxyConfig parseProxyUrl(String proxyUrl) {
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

    private String signature(Shell shell, ProfileEntity profile, ProfileEntity loaderProfile) {
        StringBuilder sb = new StringBuilder();
        sb.append("url=").append(nullToEmpty(shell.getUrl())).append(SIG_DELIMITER);
        sb.append("language=").append(effectiveLanguage(shell).getValue()).append(SIG_DELIMITER);
        sb.append("staging=").append(Boolean.TRUE.equals(shell.getStaging())).append(SIG_DELIMITER);
        sb.append("shellType=").append(nullToEmpty(shell.getShellType())).append(SIG_DELIMITER);
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
        if (loaderProfile != null) {
            sb.append("loaderProfileId=").append(loaderProfile.getId()).append(SIG_DELIMITER);
            sb.append("loaderProfileUpdatedAt=").append(nullToEmpty(loaderProfile.getUpdatedAt())).append(SIG_DELIMITER);
        }
        return sb.toString();
    }

    private String stableHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        return headers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void safeDisconnect(ShellConnection connection) {
        try {
            connection.disconnect();
        } catch (Exception e) {
            log.debug("Failed to disconnect ShellConnection", e);
        }
    }

    private record CacheEntry(String signature, ShellConnection connection) {
    }
}
