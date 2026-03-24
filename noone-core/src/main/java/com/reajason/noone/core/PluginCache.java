package com.reajason.noone.core;

import java.util.LinkedHashMap;
import java.util.Map;

public class PluginCache {
    private final Map<String, String> entries = new LinkedHashMap<>();
    private boolean initialized;

    public boolean needLoad(String pluginId) {
        return !entries.containsKey(pluginId);
    }

    public boolean isOutdated(String pluginId, String serverVersion) {
        String shellVersion = getVersion(pluginId);
        if (shellVersion == null) {
            return false;
        }
        return serverVersion != null && !serverVersion.equals(shellVersion);
    }

    public String getVersion(String pluginId) {
        if (!entries.containsKey(pluginId)) {
            return null;
        }
        return entries.get(pluginId);
    }

    public void put(String pluginId, String version) {
        entries.put(pluginId, version);
        initialized = true;
    }

    public void initialize(Map<String, String> snapshot) {
        entries.clear();
        entries.putAll(snapshot);
        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
