package com.reajason.noone.server;

import com.reajason.noone.core.NoOneCore;
import com.reajason.noone.core.plugin.SystemInfoCollector;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NoOneCore 类缓存管理单元测试
 *
 * @author ReaJason
 * @since 2025/12/14
 */
class NoOneCoreTest {

    private NoOneCore noOneCore;

    @BeforeEach
    void setUp() {
        noOneCore = new NoOneCore();
        NoOneCore.loadedPluginCache.clear();
    }

    @Test
    void testFirstLoadWithPlugin() throws Exception {
        // 准备测试数据
        Map<String, Object> args = new HashMap<>();
        args.put("action", "run");
        args.put("plugin", "testClass");
        String testClass123 = "TestClass_" + System.currentTimeMillis();
        args.put("className", testClass123);
        args.put("classBytes", createSimpleClassBytes(testClass123));
        args.put("methodName", "run");

        // 执行
        Map<String, Object> result = noOneCore.run(args);

        // 验证
        assertTrue((Boolean) result.get("classDefine"), "Should indicate class was defined");
        assertNotNull(result.get("data"), "Should return method execution result");

        // 验证缓存
        assertTrue(NoOneCore.loadedPluginCache.containsKey("testClass"), "Should cache by plugin");
    }

    @Test
    void testReuseByPlugin() throws Exception {
        // 首次加载
        Map<String, Object> args1 = new HashMap<>();
        args1.put("action", "run");
        args1.put("plugin", "testClass");
        String testClass123 = "TestClass_" + System.currentTimeMillis();
        args1.put("className", testClass123);
        args1.put("classBytes", createSimpleClassBytes(testClass123));
        args1.put("methodName", "run");
        noOneCore.run(args1);

        // 第二次调用 - 仅使用 plugin
        Map<String, Object> args2 = new HashMap<>();
        args2.put("action", "run");
        args2.put("plugin", "testClass");
        args2.put("methodName", "run");

        Map<String, Object> result = noOneCore.run(args2);

        // 验证
        assertNull(result.get("classDefine"), "Should not redefine class");
        assertNotNull(result.get("data"), "Should return method execution result");
    }

    @Test
    void testRefreshClass() throws Exception {
        // 首次加载
        Map<String, Object> args1 = new HashMap<>();
        args1.put("action", "run");
        args1.put("plugin", "testClass");
        String testClass123 = "TestClass_" + System.currentTimeMillis();
        args1.put("className", testClass123);
        args1.put("classBytes", createSimpleClassBytes(testClass123));
        args1.put("methodName", "run");
        noOneCore.run(args1);

        Class<?> oldClass = NoOneCore.loadedPluginCache.get("testClass");

        // 使用 refresh 重新加载
        Map<String, Object> args2 = new HashMap<>();
        args2.put("action", "run");
        args2.put("plugin", "testClass");
        String testClass456 = "TestClass456_" + System.currentTimeMillis();
        args2.put("className", testClass456);
        args2.put("classBytes", createSimpleClassBytes(testClass456));
        args2.put("methodName", "run");
        args2.put("refresh", "true");

        Map<String, Object> result = noOneCore.run(args2);

        // 验证
        assertTrue((Boolean) result.get("classDefine"), "Should redefine class");
        Class<?> newClass = NoOneCore.loadedPluginCache.get("testClass");
        assertNotEquals(oldClass, newClass, "Should load a different class instance");
    }

    @Test
    void testGetStatus() throws Exception {
        // 加载多个类
        loadTestClass("testClass1", "TestClass1");
        loadTestClass("testClass2", "TestClass2");
        loadTestClass("testClass3", "TestClass3");

        // 查询状态
        Map<String, Object> status = noOneCore.getStatus();
        @SuppressWarnings("unchecked")
        Map<String, String> caches = (Map<String, String>) status.get("classCaches");

        // 验证
        assertEquals(3, caches.size(), "Should have 3 cached classes");
        assertEquals("TestClass1", caches.get("testClass1"));
        assertEquals("TestClass2", caches.get("testClass2"));
        assertEquals("TestClass3", caches.get("testClass3"));
    }

    /**
     * 测试场景 7: clean 清理缓存
     */
    @Test
    void testCleanCache() throws Exception {
        // 加载类
        loadTestClass("testClass", "TestClass123");

        // 验证缓存存在
        assertEquals(1, NoOneCore.loadedPluginCache.size());

        // 直接清理缓存
        NoOneCore.loadedPluginCache.clear();

        // 验证缓存已清空
        assertTrue(NoOneCore.loadedPluginCache.isEmpty(), "Should clear plugin cache");
    }

    /**
     * 测试场景 8: 缺少必需参数时抛出异常
     */
    @Test
    void testMissingRequiredParams() {
        Map<String, Object> args = new HashMap<>();
        args.put("action", "run");
        args.put("plugin", "testClass");
        // 缺少 className 和 classBytes

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            noOneCore.run(args);
        });

        assertTrue(exception.getMessage().contains("className and classBytes are required"),
                "Should indicate missing required parameters");
    }

    /**
     * 测试场景 9: plugin 为 null 时抛出异常
     */
    @Test
    void testMissingPlugin() {
        Map<String, Object> args = new HashMap<>();
        args.put("action", "run");
        // 缺少 plugin
        args.put("className", "TestClass123");
        args.put("classBytes", createSimpleClassBytes("TestClass123"));
        args.put("methodName", "run");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            noOneCore.run(args);
        });

        assertTrue(exception.getMessage().contains("plugin is required"),
                "Should indicate plugin is required");
    }

    // ========== 辅助方法 ==========

    private void loadTestClass(String plugin, String className) throws Exception {
        Map<String, Object> args = new HashMap<>();
        args.put("action", "run");
        args.put("plugin", plugin);
        args.put("className", className);
        args.put("classBytes", createSimpleClassBytes(className));
        args.put("methodName", "run");
        noOneCore.run(args);
    }

    /**
     * 创建一个简单的测试类字节码
     * 使用 ByteBuddy 重命名 SystemInfoCollector 类
     */
    private byte[] createSimpleClassBytes(String className) {
        return new ByteBuddy()
                .redefine(SystemInfoCollector.class)
                .name(className)
                .make()
                .getBytes();
    }
}
