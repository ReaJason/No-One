package com.reajason.noone.server.shell;

import com.reajason.noone.Constants;
import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.exception.RequestSendException;
import com.reajason.noone.core.exception.ResponseDecodeException;
import com.reajason.noone.server.plugin.BuiltinPluginRegistryService;
import com.reajason.noone.server.plugin.JavaPluginPayloadService;
import com.reajason.noone.server.plugin.Plugin;
import com.reajason.noone.server.shell.dto.ShellPluginStatusResponse;
import com.reajason.noone.server.shell.oplog.ShellOperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ShellServiceDispatchPluginTest {

    private ShellService shellService;
    private ShellRepository shellRepository;
    private ShellConnectionPool shellConnectionPool;
    private ShellStatusUpdater shellStatusUpdater;
    private ShellMapper shellMapper;
    private ShellOperationLogService shellOperationLogService;
    private JavaPluginPayloadService javaPluginPayloadService;
    private BuiltinPluginRegistryService builtinPluginRegistryService;

    @BeforeEach
    void setUp() {
        shellService = new ShellService();
        shellRepository = mock(ShellRepository.class);
        shellConnectionPool = mock(ShellConnectionPool.class);
        shellStatusUpdater = mock(ShellStatusUpdater.class);
        shellMapper = mock(ShellMapper.class);
        shellOperationLogService = mock(ShellOperationLogService.class);
        javaPluginPayloadService = mock(JavaPluginPayloadService.class);
        builtinPluginRegistryService = mock(BuiltinPluginRegistryService.class);

        ReflectionTestUtils.setField(shellService, "shellRepository", shellRepository);
        ReflectionTestUtils.setField(shellService, "shellConnectionPool", shellConnectionPool);
        ReflectionTestUtils.setField(shellService, "shellStatusUpdater", shellStatusUpdater);
        ReflectionTestUtils.setField(shellService, "shellMapper", shellMapper);
        ReflectionTestUtils.setField(shellService, "shellOperationLogService", shellOperationLogService);
        ReflectionTestUtils.setField(shellService, "javaPluginPayloadService", javaPluginPayloadService);
        ReflectionTestUtils.setField(shellService, "builtinPluginRegistryService", builtinPluginRegistryService);
    }

    @Test
    void shouldMarkErrorWhenRequestPhaseFails() {
        Long shellId = 1L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.needLoadPlugin("command-execute")).thenReturn(false);
        when(connection.runPlugin(eq("command-execute"), any()))
                .thenThrow(new RequestSendException("send failed", 2, new RuntimeException("io")));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "command-execute", Map.of("cmd", "whoami"));

        assertEquals(Constants.FAILURE, response.get(Constants.CODE));
        assertEquals("REQUEST", response.get("phase"));
        assertEquals(Boolean.TRUE, response.get("retriable"));
        verify(shellStatusUpdater).markError(shellId);
        verify(shellStatusUpdater, never()).markConnected(anyLong());
    }

    @Test
    void shouldNotChangeStatusWhenResponsePhaseFails() {
        Long shellId = 2L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.needLoadPlugin("system-info")).thenReturn(false);
        when(connection.runPlugin(eq("system-info"), any()))
                .thenThrow(new ResponseDecodeException("decode failed"));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "system-info", Map.of());

        assertEquals(Constants.FAILURE, response.get(Constants.CODE));
        assertEquals("RESPONSE", response.get("phase"));
        assertEquals(Boolean.FALSE, response.get("retriable"));
        verify(shellStatusUpdater, never()).markError(anyLong());
        verify(shellStatusUpdater, never()).markConnected(anyLong());
    }

    @Test
    void shouldNotChangeStatusWhenPluginReturnsFailureCode() {
        Long shellId = 3L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.needLoadPlugin("command-execute")).thenReturn(false);
        when(connection.runPlugin(eq("command-execute"), any()))
                .thenReturn(Map.of(Constants.CODE, Constants.FAILURE, Constants.ERROR, "remote plugin failure"));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "command-execute", Map.of("cmd", "id"));

        assertEquals(Constants.FAILURE, response.get(Constants.CODE));
        assertEquals("remote plugin failure", response.get(Constants.ERROR));
        verify(shellStatusUpdater, never()).markError(anyLong());
        verify(shellStatusUpdater, never()).markConnected(anyLong());
    }

    @Test
    void shouldReturnInternalFailureWithoutChangingStatusWhenUnexpectedExceptionOccurs() {
        Long shellId = 5L;
        Shell shell = shell(shellId);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenThrow(new IllegalStateException("pool failed"));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "system-info", Map.of());

        assertEquals(Constants.FAILURE, response.get(Constants.CODE));
        assertEquals("INTERNAL", response.get("phase"));
        assertEquals(Boolean.FALSE, response.get("retriable"));
        verify(shellStatusUpdater, never()).markError(anyLong());
        verify(shellStatusUpdater, never()).markConnected(anyLong());
    }

    @Test
    void shouldLoadPluginBeforeRunWhenNotCachedAndPluginExists() {
        Long shellId = 6L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.isPluginCacheInitialized()).thenReturn(true);
        when(connection.needLoadPlugin("system-info")).thenReturn(true);
        when(builtinPluginRegistryService.findOrRegister("system-info", "java"))
                .thenReturn(Optional.of(plugin("system-info", "plugin-bytes")));
        when(javaPluginPayloadService.buildCandidates(any(), any()))
                .thenReturn(List.of(new JavaPluginPayloadService.JavaPluginCandidate(
                        "com.reajason.noone.runtime.SystemInfoA",
                        "plugin-bytes".getBytes(StandardCharsets.UTF_8))));
        when(connection.runPlugin(eq("system-info"), any()))
                .thenReturn(Map.of(Constants.CODE, Constants.SUCCESS, Constants.DATA, Map.of()));

        shellService.dispatchPlugin(shellId, "system-info", Map.of());

        verify(connection).loadPlugin(
                eq("system-info"),
                eq("0.0.1"),
                argThat(bytes -> Arrays.equals(bytes, "plugin-bytes".getBytes(StandardCharsets.UTF_8)))
        );
        verify(connection).runPlugin(eq("system-info"), any());
        verify(shellStatusUpdater).markConnected(shellId);
    }

    @Test
    void shouldSkipLoadPluginWhenPluginMetadataDoesNotExist() {
        Long shellId = 7L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.isPluginCacheInitialized()).thenReturn(true);
        when(connection.needLoadPlugin("missing-plugin")).thenReturn(true);
        when(builtinPluginRegistryService.findOrRegister("missing-plugin", "java")).thenReturn(Optional.empty());
        when(connection.runPlugin(eq("missing-plugin"), any()))
                .thenReturn(Map.of(Constants.CODE, Constants.SUCCESS, Constants.DATA, Map.of()));

        shellService.dispatchPlugin(shellId, "missing-plugin", Map.of());

        verify(connection, never()).loadPlugin(anyString(), any(byte[].class));
        verify(connection).runPlugin(eq("missing-plugin"), any());
        verify(shellStatusUpdater).markConnected(shellId);
    }

    @Test
    void shouldInjectFailureCodeWhenResultContainsErrorWithoutCode() {
        Long shellId = 8L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.needLoadPlugin("command-execute")).thenReturn(false);
        when(connection.runPlugin(eq("command-execute"), any()))
                .thenReturn(Map.of(Constants.ERROR, "business error"));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "command-execute", Map.of("cmd", "id"));

        assertEquals(Constants.FAILURE, response.get(Constants.CODE));
        assertEquals("business error", response.get(Constants.ERROR));
        verify(shellStatusUpdater, never()).markError(anyLong());
        verify(shellStatusUpdater, never()).markConnected(anyLong());
    }

    @Test
    void shouldMarkErrorWhenPluginLoadThrowsRequestPhaseException() {
        Long shellId = 9L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.isPluginCacheInitialized()).thenReturn(true);
        when(connection.needLoadPlugin("system-info")).thenReturn(true);
        when(builtinPluginRegistryService.findOrRegister("system-info", "java"))
                .thenReturn(Optional.of(plugin("system-info", "plugin-bytes")));
        when(javaPluginPayloadService.buildCandidates(any(), any()))
                .thenReturn(List.of(new JavaPluginPayloadService.JavaPluginCandidate(
                        "com.reajason.noone.runtime.SystemInfoA",
                        "plugin-bytes".getBytes(StandardCharsets.UTF_8))));
        doThrow(new RequestSendException("send failed", 1, new RuntimeException("io")))
                .when(connection)
                .loadPlugin(eq("system-info"), eq("0.0.1"), any(byte[].class));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "system-info", Map.of());

        assertEquals(Constants.FAILURE, response.get(Constants.CODE));
        assertEquals("REQUEST", response.get("phase"));
        verify(shellStatusUpdater).markError(shellId);
        verify(shellStatusUpdater, never()).markConnected(anyLong());
    }

    @Test
    void shouldNotChangeStatusWhenPluginLoadThrowsResponsePhaseException() {
        Long shellId = 10L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.isPluginCacheInitialized()).thenReturn(true);
        when(connection.needLoadPlugin("system-info")).thenReturn(true);
        when(builtinPluginRegistryService.findOrRegister("system-info", "java"))
                .thenReturn(Optional.of(plugin("system-info", "plugin-bytes")));
        when(javaPluginPayloadService.buildCandidates(any(), any()))
                .thenReturn(List.of(new JavaPluginPayloadService.JavaPluginCandidate(
                        "com.reajason.noone.runtime.SystemInfoA",
                        "plugin-bytes".getBytes(StandardCharsets.UTF_8))));
        doThrow(new ResponseDecodeException("decode failed"))
                .when(connection)
                .loadPlugin(eq("system-info"), eq("0.0.1"), any(byte[].class));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "system-info", Map.of());

        assertEquals(Constants.FAILURE, response.get(Constants.CODE));
        assertEquals("RESPONSE", response.get("phase"));
        verify(shellStatusUpdater, never()).markError(anyLong());
        verify(shellStatusUpdater, never()).markConnected(anyLong());
    }

    @Test
    void shouldFailEarlyWhenDotnetPluginPayloadIsNotAssemblyBytes() {
        Long shellId = 11L;
        Shell shell = shell(shellId);
        shell.setLanguage(ShellLanguage.DOTNET);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.isPluginCacheInitialized()).thenReturn(true);
        when(connection.needLoadPlugin("system-info")).thenReturn(true);
        when(builtinPluginRegistryService.findOrRegister("system-info", "dotnet"))
                .thenReturn(Optional.of(plugin("system-info", "using System; class FakePlugin {}")));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "system-info", Map.of());

        assertEquals(Constants.FAILURE, response.get(Constants.CODE));
        assertEquals("INTERNAL", response.get("phase"));
        assertTrue(String.valueOf(response.get(Constants.ERROR)).contains("DOTNET plugin payload"));
        verify(connection, never()).loadPlugin(anyString(), any(byte[].class));
        verify(connection, never()).runPlugin(anyString(), any());
        verify(shellStatusUpdater, never()).markError(anyLong());
        verify(shellStatusUpdater, never()).markConnected(anyLong());
    }

    @Test
    void shouldContinueDispatchWhenPluginVersionIsOutdated() {
        Long shellId = 12L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.isPluginCacheInitialized()).thenReturn(true);
        when(connection.needLoadPlugin("command-execute")).thenReturn(false);
        when(builtinPluginRegistryService.findOrRegister("command-execute", "java"))
                .thenReturn(Optional.of(plugin("command-execute", "plugin-bytes")));
        when(connection.runPlugin(eq("command-execute"), any()))
                .thenReturn(Map.of(Constants.CODE, Constants.SUCCESS, Constants.DATA, Map.of("stdout", "whoami")));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "command-execute", Map.of("cmd", "whoami"));

        assertEquals(Constants.SUCCESS, response.get(Constants.CODE));
        verify(connection).runPlugin(eq("command-execute"), any());
    }

    @Test
    void shouldReturnPluginStatusForShell() {
        Long shellId = 13L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.test()).thenReturn(true);
        when(connection.getLoadedPluginVersion("file-manager")).thenReturn("0.0.1");
        when(connection.needLoadPlugin("file-manager")).thenReturn(false);
        when(builtinPluginRegistryService.findOrRegister("file-manager", "java"))
                .thenReturn(Optional.of(plugin("file-manager", "plugin-bytes")));

        ShellPluginStatusResponse response = shellService.getPluginStatus(shellId, "file-manager");

        assertEquals("file-manager", response.getPluginId());
        assertEquals("0.0.1", response.getServerVersion());
        assertEquals("0.0.1", response.getShellVersion());
        assertTrue(response.isLoaded());
        assertEquals(false, response.isNeedsUpdate());
    }

    @Test
    void shouldRefreshPluginWhenUserUpdatesShellPlugin() {
        Long shellId = 14L;
        Shell shell = shell(shellId);
        shell.setLanguage(ShellLanguage.NODEJS);
        ShellConnection connection = mock(ShellConnection.class);
        AtomicReference<String> shellVersion = new AtomicReference<>("0.0.0");
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.test()).thenReturn(true);
        when(connection.getLoadedPluginVersion("file-manager")).thenAnswer(invocation -> shellVersion.get());
        when(connection.needLoadPlugin("file-manager")).thenAnswer(invocation -> shellVersion.get() == null);
        doAnswer(invocation -> {
            shellVersion.set(invocation.getArgument(1, String.class));
            return null;
        }).when(connection).refreshPlugin(eq("file-manager"), eq("0.0.1"), any(byte[].class));
        when(builtinPluginRegistryService.findOrRegister("file-manager", "nodejs"))
                .thenReturn(Optional.of(plugin("file-manager", "module.exports = {};", "nodejs")));

        ShellPluginStatusResponse response = shellService.updatePlugin(shellId, "file-manager");

        assertEquals("0.0.1", response.getShellVersion());
        assertEquals(false, response.isNeedsUpdate());
        verify(connection).refreshPlugin(eq("file-manager"), eq("0.0.1"), any(byte[].class));
    }

    @Test
    void shouldLoadAutoRegisteredBuiltinPluginBeforeRun() {
        Long shellId = 15L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.isPluginCacheInitialized()).thenReturn(true);
        when(connection.needLoadPlugin("file-manager")).thenReturn(true);
        when(builtinPluginRegistryService.findOrRegister("file-manager", "java"))
                .thenReturn(Optional.of(plugin("file-manager", "plugin-bytes")));
        when(javaPluginPayloadService.buildCandidates(any(), any()))
                .thenReturn(List.of(new JavaPluginPayloadService.JavaPluginCandidate(
                        "com.reajason.noone.runtime.FileManagerA",
                        "plugin-bytes".getBytes(StandardCharsets.UTF_8))));
        when(connection.runPlugin(eq("file-manager"), any()))
                .thenReturn(Map.of(Constants.CODE, Constants.SUCCESS, Constants.DATA, Map.of("files", List.of())));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "file-manager", Map.of("op", "list", "path", "."));

        assertEquals(Constants.SUCCESS, response.get(Constants.CODE));
        verify(connection).loadPlugin(eq("file-manager"), eq("0.0.1"), any(byte[].class));
        verify(connection).runPlugin(eq("file-manager"), any());
    }

    private Shell shell(Long id) {
        Shell shell = new Shell();
        shell.setId(id);
        shell.setLanguage(ShellLanguage.JAVA);
        shell.setUrl("http://127.0.0.1/test");
        return shell;
    }

    private Plugin plugin(String pluginId, String payloadRaw) {
        return plugin(pluginId, payloadRaw, "java");
    }

    private Plugin plugin(String pluginId, String payloadRaw, String language) {
        Plugin plugin = new Plugin();
        plugin.setPluginId(pluginId);
        plugin.setVersion("0.0.1");
        plugin.setLanguage(language);
        if ("nodejs".equals(language)) {
            plugin.setPayload(payloadRaw);
        } else {
            plugin.setPayload(Base64.getEncoder().encodeToString(payloadRaw.getBytes(StandardCharsets.UTF_8)));
        }
        return plugin;
    }
}
