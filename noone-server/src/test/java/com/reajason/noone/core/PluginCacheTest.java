package com.reajason.noone.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PluginCacheTest {

    @Test
    void shouldReportNeedLoadForUnknownPlugin() {
        PluginCache cache = new PluginCache();
        assertTrue(cache.needLoad("file-manager"));
    }

    @Test
    void shouldNotNeedLoadAfterPut() {
        PluginCache cache = new PluginCache();
        cache.put("file-manager", "1.0.0");
        assertFalse(cache.needLoad("file-manager"));
    }

    @Test
    void shouldReturnVersionAfterPut() {
        PluginCache cache = new PluginCache();
        cache.put("file-manager", "1.0.0");
        assertEquals("1.0.0", cache.getVersion("file-manager"));
    }

    @Test
    void shouldDetectOutdatedPlugin() {
        PluginCache cache = new PluginCache();
        cache.put("file-manager", "1.0.0");
        assertTrue(cache.isOutdated("file-manager", "2.0.0"));
        assertFalse(cache.isOutdated("file-manager", "1.0.0"));
    }

    @Test
    void shouldInitializeFromSnapshot() {
        PluginCache cache = new PluginCache();
        assertFalse(cache.isInitialized());
        cache.initialize(Map.of("a", "1.0", "b", "2.0"));
        assertTrue(cache.isInitialized());
        assertFalse(cache.needLoad("a"));
        assertEquals("1.0", cache.getVersion("a"));
    }
}
