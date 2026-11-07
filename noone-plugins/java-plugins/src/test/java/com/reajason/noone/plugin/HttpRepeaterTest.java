package com.reajason.noone.plugin;

import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class HttpRepeaterTest {

    @Test
    void testExecute() {
        HashMap<String, Object> ctx = new HashMap<>();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HttpRepeater().execute(ctx));
        assertEquals("url is required", error.getMessage());
    }

    @Test
    void testByteBuddyRedefineWithTargetJreVersion() {
        assertDoesNotThrow(() -> new ByteBuddy()
                .redefine(HttpRepeater.class)
                .visit(TargetJreVersionVisitorWrapper.DEFAULT)
                .make()
                .getBytes());
    }
}
