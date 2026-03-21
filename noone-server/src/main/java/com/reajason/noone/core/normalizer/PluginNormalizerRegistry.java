package com.reajason.noone.core.normalizer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PluginNormalizerRegistry {
    private final Map<String, PluginNormalizer> normalizers = new HashMap<>();

    public void register(String pluginId, PluginNormalizer normalizer) {
        normalizers.put(pluginId, normalizer);
    }

    public Optional<PluginNormalizer> find(String pluginId) {
        return Optional.ofNullable(normalizers.get(pluginId));
    }
}
