package com.reajason.noone.server.plugin.registry;

import com.reajason.noone.server.plugin.Plugin;
import com.reajason.noone.server.plugin.PluginRepository;
import com.reajason.noone.server.plugin.PluginService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginRegistryServiceTest {

    @Mock
    private PluginRepository pluginRepository;

    @Mock
    private PluginRegistryProperties properties;

    @Mock
    private PluginService pluginService;

    @InjectMocks
    private PluginRegistryService pluginRegistryService;

    @Test
    void enrichCatalog_shouldMarkInstalledPluginWithCorrectVersion() {
        CatalogEntry entry = new CatalogEntry();
        entry.setId("test-plugin");
        entry.setName("Test Plugin");
        entry.setVersion("1.0.0");
        entry.setLanguage("java");

        Plugin localPlugin = Plugin.builder()
                .pluginId("test-plugin")
                .version("1.0.0")
                .language("java")
                .build();

        when(pluginRepository.findByPluginIdAndLanguage("test-plugin", "java"))
                .thenReturn(Optional.of(localPlugin));

        List<EnrichedCatalogEntry> result = pluginRegistryService.enrichCatalog(List.of(entry));

        assertThat(result).hasSize(1);
        EnrichedCatalogEntry enriched = result.get(0);
        assertThat(enriched.isInstalled()).isTrue();
        assertThat(enriched.getInstalledVersion()).isEqualTo("1.0.0");
        assertThat(enriched.isUpdateAvailable()).isFalse();
    }

    @Test
    void enrichCatalog_shouldDetectUpdateAvailable() {
        CatalogEntry entry = new CatalogEntry();
        entry.setId("test-plugin");
        entry.setName("Test Plugin");
        entry.setVersion("2.0.0");
        entry.setLanguage("java");

        Plugin localPlugin = Plugin.builder()
                .pluginId("test-plugin")
                .version("1.0.0")
                .language("java")
                .build();

        when(pluginRepository.findByPluginIdAndLanguage("test-plugin", "java"))
                .thenReturn(Optional.of(localPlugin));

        List<EnrichedCatalogEntry> result = pluginRegistryService.enrichCatalog(List.of(entry));

        assertThat(result).hasSize(1);
        EnrichedCatalogEntry enriched = result.get(0);
        assertThat(enriched.isInstalled()).isTrue();
        assertThat(enriched.getInstalledVersion()).isEqualTo("1.0.0");
        assertThat(enriched.isUpdateAvailable()).isTrue();
    }

    @Test
    void enrichCatalog_shouldMarkNonInstalledPlugin() {
        CatalogEntry entry = new CatalogEntry();
        entry.setId("new-plugin");
        entry.setName("New Plugin");
        entry.setVersion("1.0.0");
        entry.setLanguage("python");

        when(pluginRepository.findByPluginIdAndLanguage("new-plugin", "python"))
                .thenReturn(Optional.empty());

        List<EnrichedCatalogEntry> result = pluginRegistryService.enrichCatalog(List.of(entry));

        assertThat(result).hasSize(1);
        EnrichedCatalogEntry enriched = result.get(0);
        assertThat(enriched.isInstalled()).isFalse();
        assertThat(enriched.getInstalledVersion()).isNull();
        assertThat(enriched.isUpdateAvailable()).isFalse();
    }
}
