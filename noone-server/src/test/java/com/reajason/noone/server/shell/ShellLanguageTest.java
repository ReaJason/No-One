package com.reajason.noone.server.shell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShellLanguageTest {

    @Test
    void shouldParseLanguageAliases() {
        assertEquals(ShellLanguage.JAVA, ShellLanguage.fromJson("java"));
        assertEquals(ShellLanguage.JAVA, ShellLanguage.fromJson("JAVA"));
        assertEquals(ShellLanguage.NODEJS, ShellLanguage.fromJson("nodejs"));
        assertEquals(ShellLanguage.NODEJS, ShellLanguage.fromJson("node"));
        assertEquals(ShellLanguage.NODEJS, ShellLanguage.fromJson("node.js"));
        assertEquals(ShellLanguage.NODEJS, ShellLanguage.fromJson("js"));
    }

    @Test
    void shouldDefaultToJavaWhenBlank() {
        assertEquals(ShellLanguage.JAVA, ShellLanguage.fromJson(null));
        assertEquals(ShellLanguage.JAVA, ShellLanguage.fromJson(""));
        assertEquals(ShellLanguage.JAVA, ShellLanguage.fromJson("   "));
    }

    @Test
    void shouldRejectUnsupportedLanguage() {
        assertThrows(IllegalArgumentException.class, () -> ShellLanguage.fromJson("python"));
    }
}

