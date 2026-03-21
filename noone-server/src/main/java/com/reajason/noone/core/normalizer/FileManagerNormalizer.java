package com.reajason.noone.core.normalizer;

import com.reajason.noone.Constants;

import java.lang.reflect.Array;
import java.util.*;
import java.util.Base64;

public class FileManagerNormalizer {

    public Map<String, Object> normalizeArgs(Map<String, Object> args) {
        try {
            Map<String, Object> source = args == null ? new HashMap<>() : args;
            return normalizeMap(source);
        } catch (IllegalArgumentException e) {
            return localFailure("file-manager args normalization failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> normalizeResponse(Map<String, Object> response) {
        Object converted = convertBinaryToJsonSafe(response);
        if (converted instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    private Map<String, Object> normalizeMap(Map<String, Object> source) {
        Map<String, Object> normalized = new HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey();
            normalized.put(key, normalizeValue(key, entry.getValue()));
        }
        return normalized;
    }

    private Object normalizeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if ("bytes".equals(key)) {
            return toByteArray(value);
        }
        if (value instanceof Map<?, ?> mapValue) {
            return normalizeMap(toStringObjectMap(mapValue));
        }
        if (value instanceof Iterable<?>) {
            List<Object> list = new ArrayList<>();
            for (Object item : (Iterable<?>) value) {
                list.add(normalizeValue(null, item));
            }
            return list;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(normalizeValue(null, Array.get(value, i)));
            }
            return list;
        }
        return value;
    }

    private byte[] toByteArray(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof byte[] bytes) {
            return bytes;
        }
        if (raw instanceof String str) {
            return Base64.getDecoder().decode(str);
        }
        if (raw instanceof Iterable<?> iterable) {
            List<Byte> bytes = new ArrayList<>();
            for (Object item : iterable) {
                bytes.add(toSingleByte(item));
            }
            byte[] out = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) {
                out[i] = bytes.get(i);
            }
            return out;
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            byte[] out = new byte[length];
            for (int i = 0; i < length; i++) {
                out[i] = toSingleByte(Array.get(raw, i));
            }
            return out;
        }
        throw new IllegalArgumentException("unsupported bytes value type: " + raw.getClass().getName());
    }

    private byte toSingleByte(Object raw) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("bytes element is not a number: " + raw);
        }
        int value = number.intValue();
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("bytes element out of range [0,255]: " + value);
        }
        return (byte) value;
    }

    private Object convertBinaryToJsonSafe(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> copied = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                copied.put(String.valueOf(entry.getKey()), convertBinaryToJsonSafe(entry.getValue()));
            }
            return copied;
        }
        if (raw instanceof Iterable<?> iterable) {
            List<Object> copied = new ArrayList<>();
            for (Object item : iterable) {
                copied.add(convertBinaryToJsonSafe(item));
            }
            return copied;
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            List<Object> copied = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                copied.add(convertBinaryToJsonSafe(Array.get(raw, i)));
            }
            return copied;
        }
        return raw;
    }

    private Map<String, Object> localFailure(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.CODE, Constants.FAILURE);
        response.put(Constants.ERROR, message);
        return response;
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> copied = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            copied.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copied;
    }
}
