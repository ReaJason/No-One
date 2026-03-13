package com.reajason.noone.server;

import com.reajason.noone.core.NoOneCore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NoOneCoreTest {

    private NoOneCore noOneCore;

    @BeforeEach
    void setUp() {
        noOneCore = new NoOneCore();
        NoOneCore.loadedPluginCache.clear();
        NoOneCore.loadedPluginVersionCache.clear();
        NoOneCore.globalCaches.clear();
    }

    @Test
    void testFirstLoadWithPlugin() throws Exception {
        String className = "com.reajason.noone.runtime.TestPluginLoad";
        Map<String, Object> loadArgs = new HashMap<>();
        loadArgs.put("plugin", "test-plugin");
        loadArgs.put("className", className);
        loadArgs.put("pluginBytes", createSimplePluginBytes(className));
        loadArgs.put("version", "1.0.0");

        Map<String, Object> loadResult = new HashMap<>();
        Object pluginObj = noOneCore.load(loadArgs, loadResult);

        assertNotNull(pluginObj);
        assertEquals(Boolean.TRUE, loadResult.get("classDefine"));
        assertTrue(NoOneCore.loadedPluginCache.containsKey("test-plugin"));
        assertEquals("1.0.0", NoOneCore.loadedPluginVersionCache.get("test-plugin"));

        Map<String, Object> runResult = noOneCore.run(Map.of("plugin", "test-plugin", "args", new HashMap<>()));
        assertEquals("ok", runResult.get("data"));
        assertEquals(Boolean.TRUE, runResult.get("classRun"));
    }

    @Test
    void testReuseByPlugin() throws Exception {
        String className = "com.reajason.noone.runtime.TestPluginReuse";
        Map<String, Object> loadArgs = new HashMap<>();
        loadArgs.put("plugin", "test-plugin");
        loadArgs.put("className", className);
        loadArgs.put("pluginBytes", createSimplePluginBytes(className));
        loadArgs.put("version", "1.0.0");

        Map<String, Object> result1 = new HashMap<>();
        Object first = noOneCore.load(loadArgs, result1);
        Map<String, Object> result2 = new HashMap<>();
        Object second = noOneCore.load(Map.of("plugin", "test-plugin", "version", "1.0.0"), result2);

        assertSame(first, second);
        assertNull(result2.get("classDefine"));
    }

    @Test
    void testRefreshClass() throws Exception {
        String className1 = "com.reajason.noone.runtime.TestPluginRefreshA";
        String className2 = "com.reajason.noone.runtime.TestPluginRefreshB";
        Map<String, Object> initialResult = new HashMap<>();
        noOneCore.load(Map.of(
                "plugin", "test-plugin",
                "className", className1,
                "pluginBytes", createSimplePluginBytes(className1),
                "version", "1.0.0"
        ), initialResult);

        Object oldPlugin = NoOneCore.loadedPluginCache.get("test-plugin");

        Map<String, Object> refreshResult = new HashMap<>();
        Object refreshed = noOneCore.load(Map.of(
                "plugin", "test-plugin",
                "className", className2,
                "pluginBytes", createSimplePluginBytes(className2),
                "version", "2.0.0",
                "refresh", "true"
        ), refreshResult);

        assertNotSame(oldPlugin, refreshed);
        assertEquals(Boolean.TRUE, refreshResult.get("classDefine"));
        assertEquals("2.0.0", NoOneCore.loadedPluginVersionCache.get("test-plugin"));
    }

    @Test
    void testGetStatus() throws Exception {
        loadTestClass("testClass1", "com.reajason.noone.runtime.StatusOne", "1.0.0");
        loadTestClass("testClass2", "com.reajason.noone.runtime.StatusTwo", "2.0.0");
        loadTestClass("testClass3", "com.reajason.noone.runtime.StatusThree", "3.0.0");

        Map<String, Object> status = noOneCore.getStatus();
        @SuppressWarnings("unchecked")
        Map<String, String> caches = (Map<String, String>) status.get("pluginCaches");

        assertEquals(3, caches.size());
        assertEquals("1.0.0", caches.get("testClass1"));
        assertEquals("2.0.0", caches.get("testClass2"));
        assertEquals("3.0.0", caches.get("testClass3"));
    }

    @Test
    void testCleanCache() throws Exception {
        loadTestClass("testClass", "com.reajason.noone.runtime.CleanPlugin", "1.0.0");

        assertEquals(1, NoOneCore.loadedPluginCache.size());
        assertEquals(1, NoOneCore.loadedPluginVersionCache.size());

        NoOneCore.loadedPluginCache.clear();
        NoOneCore.loadedPluginVersionCache.clear();

        assertTrue(NoOneCore.loadedPluginCache.isEmpty());
        assertTrue(NoOneCore.loadedPluginVersionCache.isEmpty());
    }

    @Test
    void testMissingRequiredParams() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> noOneCore.load(Map.of("plugin", "test-plugin"), new HashMap<>()));

        assertTrue(exception.getMessage().contains("className is required"));
    }

    private void loadTestClass(String plugin, String className, String version) throws Exception {
        Map<String, Object> args = new HashMap<>();
        args.put("plugin", plugin);
        args.put("className", className);
        args.put("pluginBytes", createSimplePluginBytes(className));
        args.put("version", version);
        noOneCore.load(args, new HashMap<>());
    }

    private byte[] createSimplePluginBytes(String className) {
        String internalName = className.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor equals = writer.visitMethod(Opcodes.ACC_PUBLIC, "equals", "(Ljava/lang/Object;)Z", null, null);
        equals.visitCode();
        equals.visitVarInsn(Opcodes.ALOAD, 1);
        equals.visitTypeInsn(Opcodes.CHECKCAST, "java/util/Map");
        equals.visitLdcInsn("result");
        equals.visitLdcInsn("ok");
        equals.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "java/util/Map",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                true
        );
        equals.visitInsn(Opcodes.POP);
        equals.visitInsn(Opcodes.ICONST_1);
        equals.visitInsn(Opcodes.IRETURN);
        equals.visitMaxs(3, 2);
        equals.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }
}
