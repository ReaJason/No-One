package com.reajason.noone.core.normalizer;

import java.util.Map;

public interface PluginNormalizer {
    Map<String, Object> normalizeArgs(Map<String, Object> args);

    default Map<String, Object> normalizeResponse(Map<String, Object> response) {
        return response;
    }
}
