package com.reajason.noone.server.plugin.registry;

import com.reajason.noone.server.plugin.dto.PluginInstallRequest;
import com.reajason.noone.server.plugin.dto.PluginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plugin-registry")
@RequiredArgsConstructor
@Slf4j
public class PluginRegistryController {

    private final PluginRegistryService pluginRegistryService;
    private final PluginRegistryProperties properties;

    @GetMapping("/catalog")
    public ResponseEntity<?> getCatalog() {
        if (!properties.isEnabled()) {
            return ResponseEntity.ok(Map.of("enabled", false, "plugins", List.of()));
        }
        try {
            List<EnrichedCatalogEntry> catalog = pluginRegistryService.fetchCatalog();
            return ResponseEntity.ok(Map.of("enabled", true, "plugins", catalog));
        } catch (Exception e) {
            log.error("Failed to fetch plugin catalog", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Failed to fetch catalog: " + e.getMessage()));
        }
    }

    @PostMapping("/install")
    public ResponseEntity<?> install(@Valid @RequestBody PluginInstallRequest request) {
        if (!properties.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Plugin registry is not enabled"));
        }
        try {
            PluginResponse response = pluginRegistryService.installFromRegistry(
                    request.getPluginId(), request.getLanguage());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Failed to install plugin from registry", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Failed to install plugin: " + e.getMessage()));
        }
    }
}
