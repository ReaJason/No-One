package com.reajason.noone.server.plugin.registry;

import com.reajason.noone.server.plugin.Plugin;
import com.reajason.noone.server.plugin.PluginRepository;
import com.reajason.noone.server.plugin.PluginService;
import com.reajason.noone.server.plugin.PluginSource;
import com.reajason.noone.server.plugin.dto.PluginCreateRequest;
import com.reajason.noone.server.plugin.dto.PluginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class PluginRegistryService {

    private final PluginRegistryProperties properties;
    private final PluginRepository pluginRepository;
    private final PluginService pluginService;

    private final AtomicReference<CachedCatalog> cachedCatalog = new AtomicReference<>();

    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

    public List<EnrichedCatalogEntry> fetchCatalog() {
        List<CatalogEntry> entries = getCatalogEntries();
        return enrichCatalog(entries);
    }

    public PluginResponse installFromRegistry(String pluginId, String language) {
        List<CatalogEntry> entries = getCatalogEntries();
        CatalogEntry entry = entries.stream()
                .filter(e -> pluginId.equals(e.getId()) && language.equals(e.getLanguage()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Plugin not found in catalog: " + pluginId + " (" + language + ")"));

        RestClient restClient = RestClient.create();
        PluginCreateRequest createRequest = restClient.get()
                .uri(entry.getDownloadUrl())
                .retrieve()
                .body(PluginCreateRequest.class);

        if (createRequest == null) {
            throw new RuntimeException("Failed to download plugin from: " + entry.getDownloadUrl());
        }

        return pluginService.create(createRequest, PluginSource.REGISTRY);
    }

    List<EnrichedCatalogEntry> enrichCatalog(List<CatalogEntry> entries) {
        return entries.stream().map(entry -> {
            EnrichedCatalogEntry enriched = new EnrichedCatalogEntry();
            BeanUtils.copyProperties(entry, enriched);

            Optional<Plugin> local = pluginRepository.findByPluginIdAndLanguage(
                    entry.getId(), entry.getLanguage());

            if (local.isPresent()) {
                Plugin plugin = local.get();
                enriched.setInstalled(true);
                enriched.setInstalledVersion(plugin.getVersion());
                enriched.setUpdateAvailable(compareVersions(entry.getVersion(), plugin.getVersion()) > 0);
            } else {
                enriched.setInstalled(false);
                enriched.setUpdateAvailable(false);
            }

            return enriched;
        }).toList();
    }

    int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
        }
        return 0;
    }

    private List<CatalogEntry> getCatalogEntries() {
        CachedCatalog cached = cachedCatalog.get();
        if (cached != null && !cached.isExpired()) {
            return cached.entries();
        }

        RestClient restClient = RestClient.create();
        CatalogResponse response = restClient.get()
                .uri(properties.getCatalogUrl())
                .retrieve()
                .body(CatalogResponse.class);

        if (response == null || response.getPlugins() == null) {
            throw new RuntimeException("Failed to fetch catalog from: " + properties.getCatalogUrl());
        }

        List<CatalogEntry> entries = response.getPlugins();
        cachedCatalog.set(new CachedCatalog(entries, Instant.now()));
        return entries;
    }

    private record CachedCatalog(List<CatalogEntry> entries, Instant fetchedAt) {
        boolean isExpired() {
            return Instant.now().isAfter(fetchedAt.plusSeconds(CACHE_TTL_SECONDS));
        }
    }
}
