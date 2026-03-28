package com.reajason.noone.server.shell;

import com.reajason.noone.server.profile.ProfileRepository;
import com.reajason.noone.server.shell.dto.ShellCreateRequest;
import com.reajason.noone.server.shell.dto.ShellResponse;
import com.reajason.noone.server.shell.dto.ShellUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShellMapperClientConfigCompatTest {

    private ShellMapper shellMapper;

    @BeforeEach
    void setUp() {
        shellMapper = Mappers.getMapper(ShellMapper.class);
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        when(profileRepository.findById(anyLong())).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(shellMapper, "profileRepository", profileRepository);
    }

    @Test
    void shouldMapLegacyCreateFieldsIntoClientConfig() {
        ShellCreateRequest request = new ShellCreateRequest();
        request.setName("demo");
        request.setUrl("http://127.0.0.1/test");
        request.setLanguage(ShellLanguage.JAVA);
        request.setProfileId(1L);
        request.setProxyUrl("http://127.0.0.1:8080");
        request.setCustomHeaders(Map.of("X-Test", "yes"));
        request.setConnectTimeoutMs(1500);
        request.setReadTimeoutMs(2500);
        request.setSkipSslVerify(true);
        request.setMaxRetries(2);
        request.setRetryDelayMs(3000L);

        Shell shell = shellMapper.toEntity(request);

        assertEquals("http://127.0.0.1:8080", shell.getClientConfig().get("proxyUrl"));
        assertEquals(Map.of("X-Test", "yes"), shell.getClientConfig().get("customHeaders"));
        assertEquals(1500, shell.getClientConfig().get("connectTimeoutMs"));
        assertEquals(2500, shell.getClientConfig().get("readTimeoutMs"));
        assertEquals(true, shell.getClientConfig().get("skipSslVerify"));
        assertEquals(2, shell.getClientConfig().get("maxRetries"));
        assertEquals(3000L, shell.getClientConfig().get("retryDelayMs"));
    }

    @Test
    void shouldMergeLegacyUpdateFieldsIntoExistingClientConfig() {
        Shell shell = new Shell();
        shell.setClientConfig(new LinkedHashMap<>(Map.of(
                "proxyUrl", "http://old-proxy:8888",
                "readTimeoutMs", 4000
        )));

        ShellUpdateRequest request = new ShellUpdateRequest();
        request.setProfileId(1L);
        request.setProxyUrl("http://new-proxy:9999");
        request.setMaxRetries(5);

        shellMapper.updateEntity(shell, request);

        assertEquals("http://new-proxy:9999", shell.getClientConfig().get("proxyUrl"));
        assertEquals(4000, shell.getClientConfig().get("readTimeoutMs"));
        assertEquals(5, shell.getClientConfig().get("maxRetries"));
    }

    @Test
    void shouldExposeLegacyResponseFieldsFromClientConfig() {
        Shell shell = new Shell();
        shell.setProfileId(1L);
        shell.setLanguage(ShellLanguage.JAVA);
        shell.setClientConfig(new LinkedHashMap<>(Map.of(
                "proxyUrl", "http://127.0.0.1:8080",
                "customHeaders", Map.of("X-Test", "yes"),
                "connectTimeoutMs", 1500,
                "readTimeoutMs", 2500,
                "skipSslVerify", true,
                "maxRetries", 2,
                "retryDelayMs", 3000L
        )));

        ShellResponse response = shellMapper.toResponse(shell);

        assertEquals("http://127.0.0.1:8080", response.getProxyUrl());
        assertEquals(Map.of("X-Test", "yes"), response.getCustomHeaders());
        assertEquals(1500, response.getConnectTimeoutMs());
        assertEquals(2500, response.getReadTimeoutMs());
        assertEquals(true, response.getSkipSslVerify());
        assertEquals(2, response.getMaxRetries());
        assertEquals(3000L, response.getRetryDelayMs());
    }

    @Test
    void shouldExposeLegacyResponseFieldsFromLegacyColumnsWhenClientConfigMissing() {
        Shell shell = new Shell();
        shell.setProfileId(1L);
        shell.setLanguage(ShellLanguage.JAVA);
        shell.setProxyUrl("http://legacy-proxy:8080");
        shell.setCustomHeaders(Map.of("X-Legacy", "yes"));
        shell.setConnectTimeoutMs(1500);
        shell.setReadTimeoutMs(2500);
        shell.setSkipSslVerify(true);
        shell.setMaxRetries(2);
        shell.setRetryDelayMs(3000L);

        ShellResponse response = shellMapper.toResponse(shell);

        assertEquals("http://legacy-proxy:8080", response.getProxyUrl());
        assertEquals(Map.of("X-Legacy", "yes"), response.getCustomHeaders());
        assertEquals(1500, response.getConnectTimeoutMs());
        assertEquals(2500, response.getReadTimeoutMs());
        assertEquals(true, response.getSkipSslVerify());
        assertEquals(2, response.getMaxRetries());
        assertEquals(3000L, response.getRetryDelayMs());
    }

    @Test
    void shouldKeepClientConfigNullWhenNoLegacyOrNewFieldsAreProvided() {
        ShellCreateRequest request = new ShellCreateRequest();
        request.setName("demo");
        request.setUrl("http://127.0.0.1/test");
        request.setLanguage(ShellLanguage.JAVA);
        request.setProfileId(1L);

        Shell shell = shellMapper.toEntity(request);

        assertNull(shell.getClientConfig());
    }
}
