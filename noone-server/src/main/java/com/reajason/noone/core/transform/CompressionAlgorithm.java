package com.reajason.noone.core.transform;

import java.util.Locale;

public enum CompressionAlgorithm {
    NONE,
    GZIP,
    DEFLATE,
    LZ4;

    public static CompressionAlgorithm parse(String value) {
        if (value == null) {
            return NONE;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return NONE;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("NONE".equals(upper)) {
            return NONE;
        }
        if ("GZIP".equals(upper)) {
            return GZIP;
        }
        if ("DEFLATE".equals(upper)) {
            return DEFLATE;
        }
        if ("LZ4".equals(upper)) {
            return LZ4;
        }
        throw new IllegalArgumentException("Unsupported compression: " + value);
    }
}

