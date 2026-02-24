package com.reajason.noone.plugin;

import com.alibaba.fastjson2.JSON;
import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class SystemInfoCollectorTest {

    @Test
    void testEquals() {
        HashMap<String, Object> map = new HashMap<>();
        new SystemInfoCollector().equals(map);
        Object result = map.get("result");
        System.out.println(JSON.toJSONString(result));
    }

    @Test
    void testByteBuddyRedefineWithTargetJreVersion() {
        assertDoesNotThrow(() -> new ByteBuddy()
                .redefine(SystemInfoCollector.class)
                .visit(TargetJreVersionVisitorWrapper.DEFAULT)
                .make()
                .getBytes());
    }
}
