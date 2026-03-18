package com.reajason.noone.server.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reajason.noone.server.plugin.dto.PluginCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BuiltinPluginRegistryService {

    private final PluginRepository pluginRepository;
    private final PluginService pluginService;
    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public Optional<Plugin> findOrRegister(String pluginId, String language) {
        Optional<Plugin> existing = pluginRepository.findByPluginIdAndLanguage(pluginId, language);
        if (existing.isPresent()) {
            return existing;
        }

        Optional<PluginCreateRequest> builtinPlugin = findLatestBuiltinPlugin(pluginId, language);
        if (builtinPlugin.isEmpty()) {
            return Optional.empty();
        }

        pluginService.create(builtinPlugin.get(), PluginSource.BUILTIN);
        return pluginRepository.findByPluginIdAndLanguage(pluginId, language);
    }

    private Optional<PluginCreateRequest> findLatestBuiltinPlugin(String pluginId, String language) {
        try {
            Resource[] resources = resourceResolver.getResources(
                    "classpath*:plugins/" + language + "-" + pluginId + "-plugin-*.json"
            );
            return Arrays.stream(resources)
                    .map(this::readPluginRequest)
                    .flatMap(Optional::stream)
                    .filter(request -> pluginId.equals(request.getId()) && language.equals(request.getLanguage()))
                    .max(Comparator.comparing(PluginCreateRequest::getVersion, this::compareVersions));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load built-in plugins from resources", e);
        }
    }

    private Optional<PluginCreateRequest> readPluginRequest(Resource resource) {
        try (var inputStream = resource.getInputStream()) {
            return Optional.of(objectMapper.readValue(inputStream, PluginCreateRequest.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read built-in plugin resource: " + resource.getFilename(), e);
        }
    }

    private int compareVersions(String left, String right) {
        String[] leftParts = left.split("[^A-Za-z0-9]+");
        String[] rightParts = right.split("[^A-Za-z0-9]+");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            String leftPart = i < leftParts.length ? leftParts[i] : "0";
            String rightPart = i < rightParts.length ? rightParts[i] : "0";
            int comparison = compareVersionPart(leftPart, rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return left.compareTo(right);
    }

    private int compareVersionPart(String left, String right) {
        boolean leftNumeric = left.chars().allMatch(Character::isDigit);
        boolean rightNumeric = right.chars().allMatch(Character::isDigit);
        if (leftNumeric && rightNumeric) {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        }
        if (leftNumeric != rightNumeric) {
            return leftNumeric ? 1 : -1;
        }
        return left.compareToIgnoreCase(right);
    }
}
