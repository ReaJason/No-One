package com.reajason.noone.server.shell;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum ShellLanguage {
    JAVA("java"),
    NODEJS("nodejs");

    private final String value;

    ShellLanguage(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ShellLanguage fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return JAVA;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "java" -> JAVA;
            case "nodejs", "node", "node.js", "javascript", "js" -> NODEJS;
            default -> throw new IllegalArgumentException("Unsupported shell language: " + raw);
        };
    }
}

