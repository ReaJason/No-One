package com.reajason.noone.core.normalizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PluginNormalizerRegistryTest {

    @Test
    void shouldReturnEmptyForUnregisteredPlugin() {
        PluginNormalizerRegistry registry = new PluginNormalizerRegistry();
        assertTrue(registry.find("unknown").isEmpty());
    }

    @Test
    void shouldReturnRegisteredNormalizer() {
        PluginNormalizerRegistry registry = new PluginNormalizerRegistry();
        PluginNormalizer normalizer = args -> args;
        registry.register("test-plugin", normalizer);
        assertTrue(registry.find("test-plugin").isPresent());
        assertSame(normalizer, registry.find("test-plugin").get());
    }
}
