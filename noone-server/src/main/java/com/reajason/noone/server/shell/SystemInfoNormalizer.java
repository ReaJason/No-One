package com.reajason.noone.server.shell;

import java.util.Map;

/**
 * Normalizes OS and architecture info from different language runtimes
 * (Java, Node.js) into consistent values.
 *
 * @author ReaJason
 */
class SystemInfoNormalizer {

    private SystemInfoNormalizer() {
    }

    /**
     * Normalize OS name to a canonical form.
     * <ul>
     *   <li>Windows variants → "windows"</li>
     *   <li>Linux → "linux"</li>
     *   <li>Mac OS X / Darwin → "macos"</li>
     *   <li>Unknown → lowercased raw value</li>
     * </ul>
     */
    static String normalizeOsName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String lower = raw.toLowerCase();
        if (lower.contains("mac") || lower.contains("darwin")) {
            return "macos";
        }
        if (lower.contains("windows") || lower.contains("win")) {
            return "windows";
        }
        if (lower.equals("linux")) {
            return "linux";
        }
        return lower;
    }

    /**
     * Normalize architecture, taking the already-normalized OS name
     * into account (Windows maps amd64/x86_64 → x64, Linux/macOS → amd64).
     */
    static String normalizeArch(String raw, String normalizedOsName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String lower = raw.toLowerCase();
        boolean isWindows = "windows".equals(normalizedOsName);

        return switch (lower) {
            case "amd64", "x64", "x86_64" -> isWindows ? "x64" : "amd64";
            case "x86", "ia32" -> "x86";
            case "arm64", "aarch64" -> "arm64";
            default -> lower;
        };
    }

    /**
     * Safely extract a nested string from a plugin result map.
     * For example, {@code extractString(data, "os", "name")} extracts
     * the value at {@code data.os.name}.
     *
     * @return the string value, or null if any part of the path is missing
     */
    @SuppressWarnings("unchecked")
    static String extractString(Map<String, Object> data, String section, String key) {
        if (data == null) {
            return null;
        }
        Object sectionObj = data.get(section);
        if (!(sectionObj instanceof Map)) {
            return null;
        }
        Object value = ((Map<String, Object>) sectionObj).get(key);
        return value instanceof String s ? s : null;
    }
}
