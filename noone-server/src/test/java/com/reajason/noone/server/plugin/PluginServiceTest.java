package com.reajason.noone.server.plugin;

import com.reajason.noone.server.plugin.dto.PluginResponse;
import com.reajason.noone.server.plugin.dto.PluginUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginServiceTest {

    @Mock
    private PluginRepository pluginRepository;

    @Mock
    private PluginMapper pluginMapper;

    @InjectMocks
    private PluginService pluginService;

    @Test
    void shouldReturnPluginById() {
        Plugin plugin = buildPlugin(1L, "test-plugin", "Test Plugin");
        PluginResponse response = new PluginResponse();
        response.setId("test-plugin");
        response.setName("Test Plugin");

        when(pluginRepository.findById(1L)).thenReturn(Optional.of(plugin));
        when(pluginMapper.toResponse(plugin)).thenReturn(response);

        PluginResponse result = pluginService.getById(1L);

        assertThat(result.getId()).isEqualTo("test-plugin");
        assertThat(result.getName()).isEqualTo("Test Plugin");
        verify(pluginRepository).findById(1L);
    }

    @Test
    void shouldThrowWhenPluginNotFound() {
        when(pluginRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pluginService.getById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plugin not found: 999");
    }

    @Test
    void shouldUpdatePluginMetadata() {
        Plugin plugin = buildPlugin(1L, "test-plugin", "Old Name");
        Plugin savedPlugin = buildPlugin(1L, "test-plugin", "New Name");
        savedPlugin.setDescription("New Description");
        savedPlugin.setAuthor("New Author");

        PluginResponse response = new PluginResponse();
        response.setName("New Name");
        response.setDescription("New Description");
        response.setAuthor("New Author");

        when(pluginRepository.findById(1L)).thenReturn(Optional.of(plugin));
        when(pluginRepository.save(any(Plugin.class))).thenReturn(savedPlugin);
        when(pluginMapper.toResponse(savedPlugin)).thenReturn(response);

        PluginUpdateRequest request = new PluginUpdateRequest();
        request.setName("New Name");
        request.setDescription("New Description");
        request.setAuthor("New Author");

        PluginResponse result = pluginService.update(1L, request);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("New Description");
        assertThat(result.getAuthor()).isEqualTo("New Author");
        verify(pluginRepository).save(plugin);
    }

    @Test
    void shouldDeletePlugin() {
        Plugin plugin = buildPlugin(1L, "test-plugin", "Test Plugin");
        when(pluginRepository.findById(1L)).thenReturn(Optional.of(plugin));

        pluginService.delete(1L);

        verify(pluginRepository).delete(plugin);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentPlugin() {
        when(pluginRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pluginService.delete(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plugin not found: 999");
    }

    private Plugin buildPlugin(Long id, String pluginId, String name) {
        Plugin plugin = new Plugin();
        plugin.setId(id);
        plugin.setPluginId(pluginId);
        plugin.setName(name);
        plugin.setVersion("1.0.0");
        plugin.setLanguage("java");
        plugin.setType("action");
        return plugin;
    }
}
