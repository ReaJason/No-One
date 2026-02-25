package com.reajason.noone.server.shell;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SystemInfoNormalizerTest {

    @Nested
    class NormalizeOsName {

        @ParameterizedTest
        @CsvSource({
                "Windows Server 2016, windows",
                "Windows 10, windows",
                "Windows_NT, windows",
                "Linux, linux",
                "Mac OS X, macos",
                "Darwin, macos",
        })
        void shouldNormalizeKnownOsNames(String raw, String expected) {
            assertEquals(expected, SystemInfoNormalizer.normalizeOsName(raw));
        }

        @Test
        void shouldLowercaseUnknownOsName() {
            assertEquals("freebsd", SystemInfoNormalizer.normalizeOsName("FreeBSD"));
        }

        @Test
        void shouldReturnNullForNull() {
            assertNull(SystemInfoNormalizer.normalizeOsName(null));
        }

        @Test
        void shouldReturnNullForBlank() {
            assertNull(SystemInfoNormalizer.normalizeOsName("  "));
        }
    }

    @Nested
    class NormalizeArchWindows {

        @ParameterizedTest
        @CsvSource({
                "amd64, x64",
                "x64, x64",
                "x86_64, x64",
                "x86, x86",
                "ia32, x86",
                "arm64, arm64",
                "aarch64, arm64",
        })
        void shouldNormalizeArchForWindows(String raw, String expected) {
            assertEquals(expected, SystemInfoNormalizer.normalizeArch(raw, "windows"));
        }
    }

    @Nested
    class NormalizeArchLinux {

        @ParameterizedTest
        @CsvSource({
                "amd64, amd64",
                "x64, amd64",
                "x86_64, amd64",
                "x86, x86",
                "ia32, x86",
                "arm64, arm64",
                "aarch64, arm64",
        })
        void shouldNormalizeArchForLinux(String raw, String expected) {
            assertEquals(expected, SystemInfoNormalizer.normalizeArch(raw, "linux"));
        }

        @Test
        void shouldNormalizeArchForMacOS() {
            assertEquals("amd64", SystemInfoNormalizer.normalizeArch("x86_64", "macos"));
            assertEquals("arm64", SystemInfoNormalizer.normalizeArch("aarch64", "macos"));
        }
    }

    @Nested
    class NormalizeArchEdgeCases {

        @Test
        void shouldLowercaseUnknownArch() {
            assertEquals("mips", SystemInfoNormalizer.normalizeArch("MIPS", "linux"));
        }

        @Test
        void shouldReturnNullForNull() {
            assertNull(SystemInfoNormalizer.normalizeArch(null, "linux"));
        }

        @Test
        void shouldReturnNullForBlank() {
            assertNull(SystemInfoNormalizer.normalizeArch("  ", "linux"));
        }
    }

    @Nested
    class ExtractString {

        @Test
        void shouldExtractNestedValue() {
            Map<String, Object> data = Map.of("os", Map.of("name", "Linux", "arch", "amd64"));
            assertEquals("Linux", SystemInfoNormalizer.extractString(data, "os", "name"));
            assertEquals("amd64", SystemInfoNormalizer.extractString(data, "os", "arch"));
        }

        @Test
        void shouldReturnNullForMissingSection() {
            Map<String, Object> data = Map.of("runtime", Map.of("type", "jdk"));
            assertNull(SystemInfoNormalizer.extractString(data, "os", "name"));
        }

        @Test
        void shouldReturnNullForMissingKey() {
            Map<String, Object> data = Map.of("os", Map.of("name", "Linux"));
            assertNull(SystemInfoNormalizer.extractString(data, "os", "arch"));
        }

        @Test
        void shouldReturnNullForNullData() {
            assertNull(SystemInfoNormalizer.extractString(null, "os", "name"));
        }

        @Test
        void shouldReturnNullWhenSectionIsNotAMap() {
            Map<String, Object> data = Map.of("os", "not-a-map");
            assertNull(SystemInfoNormalizer.extractString(data, "os", "name"));
        }
    }
}
