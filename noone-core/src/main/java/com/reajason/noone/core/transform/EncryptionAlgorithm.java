package com.reajason.noone.core.transform;

import java.util.Locale;

public enum EncryptionAlgorithm {
    NONE,
    XOR,
    AES,
    TRIPLE_DES;

    public static EncryptionAlgorithm parse(String value) {
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
        if ("XOR".equals(upper)) {
            return XOR;
        }
        if ("AES".equals(upper)) {
            return AES;
        }
        if ("TRIPLEDES".equals(upper) || "TRIPLE_DES".equals(upper) || "3DES".equals(upper) || "DES3".equals(upper)) {
            return TRIPLE_DES;
        }
        throw new IllegalArgumentException("Unsupported encryption: " + value);
    }

    public int keyLengthBytes() {
        return switch (this) {
            case NONE -> 0;
            case XOR -> 32;
            case AES -> 16;
            case TRIPLE_DES -> 24;
        };
    }
}

