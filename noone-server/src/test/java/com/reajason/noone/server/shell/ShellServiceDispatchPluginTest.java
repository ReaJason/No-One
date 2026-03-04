package com.reajason.noone.server.shell;

import com.reajason.noone.Constants;
import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.exception.RequestSendException;
import com.reajason.noone.core.exception.ResponseDecodeException;
import com.reajason.noone.server.admin.plugin.Plugin;
import com.reajason.noone.server.admin.plugin.PluginRepository;
import com.reajason.noone.server.shell.oplog.ShellOperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private PluginRepository pluginRepository;
    private ShellStatusUpdater shellStatusUpdater;
    private ShellMapper shellMapper;
    private ShellOperationLogService shellOperationLogService;

    @BeforeEach
    void setUp() {
        shellService = new ShellService();
        shellRepository = mock(ShellRepository.class);
        shellConnectionPool = mock(ShellConnectionPool.class);
        pluginRepository = mock(PluginRepository.class);
        shellStatusUpdater = mock(ShellStatusUpdater.class);
        shellMapper = mock(ShellMapper.class);
        shellOperationLogService = mock(ShellOperationLogService.class);

        ReflectionTestUtils.setField(shellService, "shellRepository", shellRepository);
        ReflectionTestUtils.setField(shellService, "shellConnectionPool", shellConnectionPool);
        ReflectionTestUtils.setField(shellService, "pluginRepository", pluginRepository);
        ReflectionTestUtils.setField(shellService, "shellStatusUpdater", shellStatusUpdater);
        ReflectionTestUtils.setField(shellService, "shellMapper", shellMapper);
        ReflectionTestUtils.setField(shellService, "shellOperationLogService", shellOperationLogService);
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
        when(connection.needLoadPlugin("system-info")).thenReturn(true);
        when(pluginRepository.findByPluginIdAndLanguage("system-info", "java"))
                .thenReturn(Optional.of(plugin("system-info", "plugin-bytes")));
        when(connection.runPlugin(eq("system-info"), any()))
                .thenReturn(Map.of(Constants.CODE, Constants.SUCCESS, Constants.DATA, Map.of()));

        shellService.dispatchPlugin(shellId, "system-info", Map.of());

        verify(connection).loadPlugin(
                eq("system-info"),
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
        when(connection.needLoadPlugin("missing-plugin")).thenReturn(true);
        when(pluginRepository.findByPluginIdAndLanguage("missing-plugin", "java")).thenReturn(Optional.empty());
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
        when(connection.needLoadPlugin("system-info")).thenReturn(true);
        when(pluginRepository.findByPluginIdAndLanguage("system-info", "java"))
                .thenReturn(Optional.of(plugin("system-info", "plugin-bytes")));
        doThrow(new RequestSendException("send failed", 1, new RuntimeException("io")))
                .when(connection)
                .loadPlugin(eq("system-info"), any(byte[].class));

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
        when(connection.needLoadPlugin("system-info")).thenReturn(true);
        when(pluginRepository.findByPluginIdAndLanguage("system-info", "java"))
                .thenReturn(Optional.of(plugin("system-info", "plugin-bytes")));
        doThrow(new ResponseDecodeException("decode failed"))
                .when(connection)
                .loadPlugin(eq("system-info"), any(byte[].class));

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
        when(connection.needLoadPlugin("system-info")).thenReturn(true);
        when(pluginRepository.findByPluginIdAndLanguage("system-info", "dotnet"))
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

    private Shell shell(Long id) {
        Shell shell = new Shell();
        shell.setId(id);
        shell.setLanguage(ShellLanguage.JAVA);
        shell.setUrl("http://127.0.0.1/test");
        return shell;
    }

    private Plugin plugin(String pluginId, String payloadRaw) {
        Plugin plugin = new Plugin();
        plugin.setPluginId(pluginId);
        plugin.setPayload(Base64.getEncoder().encodeToString(payloadRaw.getBytes(StandardCharsets.UTF_8)));
        return plugin;
    }
}
