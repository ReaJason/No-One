package com.reajason.noone.plugin;

import com.alibaba.fastjson2.JSON;
import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class ProcessMonitorTest {

    @Test
    void testExecute() {
        HashMap<String, Object> ctx = new HashMap<>();
        Object result = new ProcessMonitor().execute(ctx);
        assertNotNull(result);
        System.out.println(JSON.toJSONString(result));
    }

    @Test
    void testByteBuddyRedefineWithTargetJreVersion() {
        assertDoesNotThrow(() -> new ByteBuddy()
                .redefine(ProcessMonitor.class)
                .visit(TargetJreVersionVisitorWrapper.DEFAULT)
                .make()
                .getBytes());
    }
}
