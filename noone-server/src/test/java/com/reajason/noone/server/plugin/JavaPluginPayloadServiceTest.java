package com.reajason.noone.server.plugin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaPluginPayloadServiceTest {

    private final JavaPluginPayloadService service = new JavaPluginPayloadService();

    @Test
    void shouldRewriteJavaPluginBytecodeToCandidateClassNames() {
        Plugin plugin = new Plugin();
        plugin.setMeta(Map.of(
                "classNames",
                List.of("com.reajason.noone.runtime.TestPluginA", "com.reajason.noone.runtime.TestPluginB")
        ));

        List<JavaPluginPayloadService.JavaPluginCandidate> candidates =
                service.buildCandidates(plugin, simpleClassBytes("com.reajason.noone.plugin.TestPlugin"));

        assertEquals(Set.of("com.reajason.noone.runtime.TestPluginA", "com.reajason.noone.runtime.TestPluginB"),
                candidates.stream().map(JavaPluginPayloadService.JavaPluginCandidate::className).collect(Collectors.toSet()));
        for (JavaPluginPayloadService.JavaPluginCandidate candidate : candidates) {
            ClassReader reader = new ClassReader(candidate.payloadBytes());
            assertEquals(candidate.className().replace('.', '/'), reader.getClassName());
        }
    }

    private byte[] simpleClassBytes(String className) {
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
        equals.visitInsn(Opcodes.ICONST_1);
        equals.visitInsn(Opcodes.IRETURN);
        equals.visitMaxs(1, 2);
        equals.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }
}
