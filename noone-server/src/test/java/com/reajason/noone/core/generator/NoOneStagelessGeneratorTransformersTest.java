package com.reajason.noone.core.generator;

import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.core.generator.memshell.NoOneStagelessGenerator;
import com.reajason.noone.core.shelltool.NoOneStagelessServlet;
import com.reajason.noone.core.transform.*;
import com.reajason.noone.server.profile.Profile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoOneStagelessGeneratorTransformersTest {

    @Test
    void generatedServlet_shouldApplyRequestAndResponseTransformers() throws Exception {
        List<String> transformations = List.of(
                CompressionAlgorithm.LZ4.name(),
                EncryptionAlgorithm.TRIPLE_DES.name(),
                EncodingAlgorithm.BIG_INTEGER.name()
        );

        ShellConfig shellConfig = ShellConfig.builder()
                .server("Tomcat")
                .shellTool("Custom")
                .shellType("Servlet")
                .targetJreVersion(52)
                .build();

        Profile profile = new Profile();
        profile.setPassword("secret");
        profile.setRequestTransformations(transformations);
        profile.setResponseTransformations(transformations);

        NoOneConfig noOneConfig = new NoOneConfig();
        noOneConfig.setShellClass(NoOneStagelessServlet.class);
        noOneConfig.setShellClassName("com.reajason.noone.test.GeneratedNoOneServletTransformers");
        noOneConfig.setCoreProfile(profile);

        NoOneStagelessGenerator generator = new NoOneStagelessGenerator(shellConfig, noOneConfig);
        byte[] bytes = generator.getBytes();
//        Files.write(Paths.get("transformations.class"), bytes);
        Class<?> generated = loadGeneratedClass(bytes);
        Object instance = generated.getDeclaredConstructor().newInstance();

        Method transformReqPayload = generated.getDeclaredMethod("transformReqPayload", byte[].class);
        transformReqPayload.setAccessible(true);
        Method transformResData = generated.getDeclaredMethod("transformResData", byte[].class);
        transformResData.setAccessible(true);

        byte[] original = "PAYLOAD".getBytes(StandardCharsets.UTF_8);

        TransformationSpec spec = TransformationSpec.parse(transformations);

        byte[] clientOutbound = TrafficTransformer.outbound(original, spec, "secret");
        byte[] serverInbound = (byte[]) transformReqPayload.invoke(instance, clientOutbound);
        assertArrayEquals(original, serverInbound);

        byte[] serverOutbound = (byte[]) transformResData.invoke(instance, original);
        byte[] clientInbound = TrafficTransformer.inbound(serverOutbound, spec, "secret");
        assertArrayEquals(original, clientInbound);
    }

    @Test
    void parse_shouldThrowException_whenListHasOneElement() {
        List<String> transformations = List.of("Gzip");

        assertThrows(IllegalArgumentException.class, () -> TransformationSpec.parse(transformations));
    }

    @Test
    void parse_shouldThrowException_whenListHasTwoElements() {
        List<String> transformations = List.of("Gzip", "AES");

        assertThrows(IllegalArgumentException.class, () -> TransformationSpec.parse(transformations));
    }

    @Test
    void parse_shouldThrowException_whenListHasFourElements() {
        List<String> transformations = List.of("Gzip", "AES", "Base64", "Extra");

        assertThrows(IllegalArgumentException.class, () -> TransformationSpec.parse(transformations));
    }

    private static Class<?> loadGeneratedClass(byte[] bytes) {
        String internalName = new ClassReader(bytes).getClassName();
        String className = internalName.replace('/', '.');

        class DefiningClassLoader extends ClassLoader {
            DefiningClassLoader(ClassLoader parent) {
                super(parent);
            }

            Class<?> define() {
                return defineClass(className, bytes, 0, bytes.length);
            }
        }

        return new DefiningClassLoader(NoOneStagelessServlet.class.getClassLoader()).define();
    }
}

