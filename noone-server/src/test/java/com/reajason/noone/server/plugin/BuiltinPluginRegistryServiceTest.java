package com.reajason.noone.server.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reajason.noone.server.plugin.dto.PluginResponse;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BuiltinPluginRegistryServiceTest {

    @Test
    void shouldReturnExistingPluginWithoutRegisteringBuiltin() {
        PluginRepository pluginRepository = mock(PluginRepository.class);
        PluginService pluginService = mock(PluginService.class);
        BuiltinPluginRegistryService service = new BuiltinPluginRegistryService(
                pluginRepository,
                pluginService,
                new ObjectMapper()
        );
        Plugin plugin = plugin("system-info", "0.0.9", "java");
        when(pluginRepository.findByPluginIdAndLanguage("system-info", "java")).thenReturn(Optional.of(plugin));

        Optional<Plugin> result = service.findOrRegister("system-info", "java");

        assertTrue(result.isPresent());
        assertEquals("0.0.9", result.get().getVersion());
        verify(pluginService, never()).create(any());
    }

    @Test
    void shouldRegisterLatestBuiltinPluginWhenServerRecordIsMissing() {
        PluginRepository pluginRepository = mock(PluginRepository.class);
        PluginService pluginService = mock(PluginService.class);
        BuiltinPluginRegistryService service = new BuiltinPluginRegistryService(
                pluginRepository,
                pluginService,
                new ObjectMapper()
        );
        Plugin registered = plugin("file-manager", "0.0.1", "java");
        when(pluginRepository.findByPluginIdAndLanguage("file-manager", "java"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(registered));
        when(pluginService.create(any())).thenReturn(new PluginResponse());

        Optional<Plugin> result = service.findOrRegister("file-manager", "java");

        assertTrue(result.isPresent());
        assertEquals("0.0.1", result.get().getVersion());
        verify(pluginService).create(argThat(request ->
                "file-manager".equals(request.getId())
                        && "java".equals(request.getLanguage())
                        && "0.0.1".equals(request.getVersion())
        ));
    }

    @Test
    void shouldReturnEmptyWhenBuiltinPluginDoesNotExist() {
        PluginRepository pluginRepository = mock(PluginRepository.class);
        PluginService pluginService = mock(PluginService.class);
        BuiltinPluginRegistryService service = new BuiltinPluginRegistryService(
                pluginRepository,
                pluginService,
                new ObjectMapper()
        );
        when(pluginRepository.findByPluginIdAndLanguage("not-exists", "java")).thenReturn(Optional.empty());

        Optional<Plugin> result = service.findOrRegister("not-exists", "java");

        assertFalse(result.isPresent());
        verify(pluginService, never()).create(any());
    }

    private Plugin plugin(String pluginId, String version, String language) {
        Plugin plugin = new Plugin();
        plugin.setPluginId(pluginId);
        plugin.setVersion(version);
        plugin.setLanguage(language);
        return plugin;
    }
}
