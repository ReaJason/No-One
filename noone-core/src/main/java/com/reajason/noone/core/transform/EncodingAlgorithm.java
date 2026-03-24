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
        if ("NONE".equals(upper)) {
            return NONE;
        }
        if ("BASE64".equals(upper)) {
            return BASE64;
        }
        if ("HEX".equals(upper)) {
            return HEX;
        }
        if ("BIGINTEGER".equals(upper) || "BIG_INTEGER".equals(upper) || "BIGINT".equals(upper)) {
            return BIG_INTEGER;
        }
        throw new IllegalArgumentException("Unsupported encoding: " + value);
    }
}

