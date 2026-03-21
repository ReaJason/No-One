package com.reajason.noone.plugin;

import com.alibaba.fastjson2.JSON;
import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class HttpRepeaterTest {

    @Test
    void testEquals() {
        HashMap<String, Object> ctx = new HashMap<>();
        new HttpRepeater().equals(ctx);
        Object result = ctx.get("result");
        assertNotNull(result);
        System.out.println(JSON.toJSONString(result));
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
