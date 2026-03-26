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
        return switch (upper) {
            case "NONE" -> NONE;
            case "GZIP" -> GZIP;
            case "DEFLATE" -> DEFLATE;
            case "LZ4" -> LZ4;
            default -> throw new IllegalArgumentException("Unsupported compression: " + value);
        };
    }
}

