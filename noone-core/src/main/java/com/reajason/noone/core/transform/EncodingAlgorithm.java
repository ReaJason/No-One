package com.reajason.noone.core.transform;

import java.util.Locale;

public enum EncodingAlgorithm {
    NONE,
    BASE64,
    HEX,
    BIG_INTEGER;

    public static EncodingAlgorithm parse(String value) {
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
            case "BASE64" -> BASE64;
            case "HEX" -> HEX;
            case "BIGINTEGER", "BIG_INTEGER", "BIGINT" -> BIG_INTEGER;
            default -> throw new IllegalArgumentException("Unsupported encoding: " + value);
        };
    }
}

