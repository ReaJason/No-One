package com.reajason.noone.server.shell;

import com.reajason.noone.Constants;
import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.exception.RequestSendException;
import com.reajason.noone.core.exception.ResponseDecodeException;
import com.reajason.noone.server.admin.plugin.Plugin;
import com.reajason.noone.server.admin.plugin.PluginRepository;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

    @BeforeEach
    void setUp() {
        shellService = new ShellService();
        shellRepository = mock(ShellRepository.class);
        shellConnectionPool = mock(ShellConnectionPool.class);
        pluginRepository = mock(PluginRepository.class);
        shellStatusUpdater = mock(ShellStatusUpdater.class);
        shellMapper = mock(ShellMapper.class);

        ReflectionTestUtils.setField(shellService, "shellRepository", shellRepository);
        ReflectionTestUtils.setField(shellService, "shellConnectionPool", shellConnectionPool);
        ReflectionTestUtils.setField(shellService, "pluginRepository", pluginRepository);
        ReflectionTestUtils.setField(shellService, "shellStatusUpdater", shellStatusUpdater);
        ReflectionTestUtils.setField(shellService, "shellMapper", shellMapper);
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
    void shouldMarkConnectedWhenPluginReturnsSuccessCode() {
        Long shellId = 4L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.needLoadPlugin("system-info")).thenReturn(false);

        Map<String, Object> data = new HashMap<>();
        data.put("os", Map.of("name", "Windows Server 2016", "arch", "amd64"));
        data.put("runtime", Map.of("type", "jdk", "version", "1.8.0_291"));
        when(connection.runPlugin(eq("system-info"), any()))
                .thenReturn(Map.of(Constants.CODE, Constants.SUCCESS, Constants.DATA, data));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "system-info", Map.of());

        assertEquals(Constants.SUCCESS, response.get(Constants.CODE));
        verify(shellStatusUpdater).markConnected(shellId);
        verify(shellStatusUpdater, never()).markError(anyLong());
        verify(shellStatusUpdater).updateBasicInfo(eq(shellId), eq(Map.of(
                "os", "windows",
                "arch", "x64",
                "runtimeType", "jdk",
                "runtimeVersion", "1.8.0_291"
        )));
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
    void shouldNotCallUpdateBasicInfoForNonSystemInfoPlugin() {
        Long shellId = 11L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.needLoadPlugin("command-execute")).thenReturn(false);
        when(connection.runPlugin(eq("command-execute"), any()))
                .thenReturn(Map.of(Constants.CODE, Constants.SUCCESS, Constants.DATA, Map.of("output", "root")));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "command-execute", Map.of("cmd", "whoami"));

        assertEquals(Constants.SUCCESS, response.get(Constants.CODE));
        verify(shellStatusUpdater).markConnected(shellId);
        verify(shellStatusUpdater, never()).updateBasicInfo(anyLong(), any());
    }

    @Test
    void shouldStillSucceedWhenSystemInfoDataHasUnexpectedStructure() {
        Long shellId = 12L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.needLoadPlugin("system-info")).thenReturn(false);
        // data is a string instead of expected nested map
        when(connection.runPlugin(eq("system-info"), any()))
                .thenReturn(Map.of(Constants.CODE, Constants.SUCCESS, Constants.DATA, "unexpected-string"));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "system-info", Map.of());

        assertEquals(Constants.SUCCESS, response.get(Constants.CODE));
        verify(shellStatusUpdater).markConnected(shellId);
        // updateBasicInfo should not be called because data is not a Map
        verify(shellStatusUpdater, never()).updateBasicInfo(anyLong(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldConvertFileManagerByteArrayResponseToIntegerList() {
        Long shellId = 13L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.needLoadPlugin("file-manager")).thenReturn(false);
        when(connection.runPlugin(eq("file-manager"), any())).thenReturn(Map.of(
                Constants.CODE, Constants.SUCCESS,
                Constants.DATA, Map.of(
                        "bytes", new byte[]{0, 1, (byte) 255},
                        "chunks", List.of(Map.of("bytes", new byte[]{2, 3}))
                )
        ));

        Map<String, Object> response = shellService.dispatchPlugin(shellId, "file-manager", Map.of("op", "read-all"));

        assertEquals(Constants.SUCCESS, response.get(Constants.CODE));
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.DATA);
        assertInstanceOf(List.class, data.get("bytes"));
        assertEquals(List.of(0, 1, 255), data.get("bytes"));
        List<Map<String, Object>> chunks = (List<Map<String, Object>>) data.get("chunks");
        assertEquals(List.of(2, 3), chunks.get(0).get("bytes"));
        verify(shellStatusUpdater).markConnected(shellId);
        verify(shellStatusUpdater, never()).updateBasicInfo(anyLong(), any());
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
