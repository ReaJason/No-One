package com.reajason.noone.noone.core;

import com.reajason.noone.core.NoOneCore;
import com.reajason.noone.core.TlvCodec;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TlvCodecTest {

    @Test
    void shouldRoundTripAllSupportedTypes() {
        Set<String> tags = new LinkedHashSet<>();
        tags.add("a");
        tags.add("b");

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nullVal", null);
        map.put("string", "hello");
        map.put("int", 42);
        map.put("long", 123456789L);
        map.put("double", 3.14);
        map.put("bool", true);
        map.put("bytes", new byte[]{1, 2, 3});
        map.put("list", List.of("x", "y"));
        map.put("array", new Object[]{"a", 1});
        map.put("set", tags);
        map.put("nested", Map.of("key", "value"));

        byte[] serialized = TlvCodec.serialize(map);
        Map<String, Object> decoded = TlvCodec.deserialize(serialized);

        assertNull(decoded.get("nullVal"));
        assertEquals("hello", decoded.get("string"));
        assertEquals(42, decoded.get("int"));
        assertEquals(123456789L, decoded.get("long"));
        assertEquals(3.14, decoded.get("double"));
        assertEquals(true, decoded.get("bool"));
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) decoded.get("bytes"));
        assertEquals(List.of("x", "y"), decoded.get("list"));
        assertArrayEquals(new Object[]{"a", 1}, (Object[]) decoded.get("array"));
        assertTrue(decoded.get("set") instanceof Set);
        assertEquals(tags, decoded.get("set"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) decoded.get("nested");
        assertEquals("value", nested.get("key"));
    }

    @Test
    void shouldRoundTripEmptySet() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("empty", new LinkedHashSet<>());

        byte[] serialized = TlvCodec.serialize(map);
        Map<String, Object> decoded = TlvCodec.deserialize(serialized);

        assertTrue(decoded.get("empty") instanceof Set);
        @SuppressWarnings("unchecked")
        Set<Object> emptySet = (Set<Object>) decoded.get("empty");
        assertTrue(emptySet.isEmpty());
    }

    @Test
    void shouldPreserveSetInsertionOrder() {
        Set<String> ordered = new LinkedHashSet<>();
        ordered.add("cherry");
        ordered.add("apple");
        ordered.add("banana");

        Map<String, Object> map = Map.of("fruits", ordered);

        byte[] serialized = TlvCodec.serialize(map);
        Map<String, Object> decoded = TlvCodec.deserialize(serialized);

        @SuppressWarnings("unchecked")
        Set<String> decodedSet = (Set<String>) decoded.get("fruits");
        assertEquals(List.of("cherry", "apple", "banana"), new ArrayList<>(decodedSet));
    }

    @Test
    void shouldBeCompatibleWithNoOneCoreSerialization() throws Exception {
        NoOneCore core = new NoOneCore();

        Map<String, Object> plugins = new LinkedHashMap<>();
        plugins.put("plugin-a", "0.0.1");
        plugins.put("plugin-b", "0.0.2");

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pluginCaches", plugins);
        map.put("code", 0);

        // NoOneCore serialize -> TlvCodec deserialize
        byte[] coreBytes = core.serialize(map);
        Map<String, Object> decodedByCodec = TlvCodec.deserialize(coreBytes);
        assertTrue(decodedByCodec.get("pluginCaches") instanceof Map);
        assertEquals(plugins, decodedByCodec.get("pluginCaches"));

        // TlvCodec serialize -> NoOneCore deserialize
        byte[] codecBytes = TlvCodec.serialize(map);
        Map<String, Object> decodedByCore = core.deserialize(codecBytes);
        assertTrue(decodedByCore.get("pluginCaches") instanceof Map);
        assertEquals(plugins, decodedByCore.get("pluginCaches"));
    }
}
