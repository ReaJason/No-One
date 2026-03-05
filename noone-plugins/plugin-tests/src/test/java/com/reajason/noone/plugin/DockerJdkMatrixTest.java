package com.reajason.noone.plugin;

import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Docker-based JDK matrix test for plugin compatibility.
 * <p>
 * Verifies that each plugin class, compiled to JDK 1.6 bytecode via ByteBuddy,
 * can be loaded and executed (defineClass → newInstance → equals(Map)) inside
 * Docker containers running various JDK versions.
 * <p>
 * Run via: ./gradlew :noone-plugins:plugin-tests:dockerJdkMatrixTest
 */
@Tag("docker-jdk-matrix")
class DockerJdkMatrixTest {

    private static final String WORK_DIR = "/work";

    /**
     * Plugin classes to test. Each entry: [pluginName, pluginClass,
     * contextFactory].
     */
    private static final List<PluginTestCase> PLUGIN_TEST_CASES = Arrays.asList(
            new PluginTestCase("SystemInfoCollector", SystemInfoCollector.class, HashMap::new),
            new PluginTestCase("CommandExecutor", CommandExecutor.class, DockerJdkMatrixTest::commandExecutorContext),
            new PluginTestCase("ThreadDumpCollector", ThreadDumpCollector.class, HashMap::new));

    // ByteBuddy-compiled JDK 1.6 bytecodes
    private static final Map<String, byte[]> pluginBytesMap = new LinkedHashMap<>();
    private static byte[] runnerBytes;

    @BeforeAll
    static void compileAll() {
        ByteBuddy byteBuddy = new ByteBuddy();

        // Compile PluginRunner to JDK 1.6
        runnerBytes = byteBuddy
                .redefine(PluginRunner.class)
                .visit(TargetJreVersionVisitorWrapper.DEFAULT)
                .make()
                .getBytes();

        // Compile each plugin class to JDK 1.6
        for (PluginTestCase testCase : PLUGIN_TEST_CASES) {
            byte[] bytes = byteBuddy
                    .redefine(testCase.pluginClass)
                    .visit(TargetJreVersionVisitorWrapper.DEFAULT)
                    .make()
                    .getBytes();
            pluginBytesMap.put(testCase.pluginClass.getName(), bytes);
        }
    }

    @ParameterizedTest(name = "{0} | {1} | JDK {2}")
    @MethodSource("jdkPluginMatrix")
    void pluginShouldLoadAndRunOnJdk(String caseName, String pluginName,
            int targetJdk, String dockerImage,
            Class<?> pluginClass,
            Map<String, Object> inputContext) throws Exception {
        String className = pluginClass.getName();
        byte[] pluginBytes = pluginBytesMap.get(className);
        assertNotNull(pluginBytes, "No compiled bytes for " + className);

        // Serialize input context
        byte[] inputSer = serializeToBytes(inputContext);

        // Class file paths inside container
        String runnerClassPath = WORK_DIR + "/" + PluginRunner.class.getName().replace('.', '/') + ".class";
        String pluginClassPath = WORK_DIR + "/" + className.replace('.', '/') + ".class";

        try (GenericContainer<?> container = new GenericContainer<>(dockerImage)) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("sleep").withCmd("infinity"));
            container.withCopyToContainer(Transferable.of(runnerBytes), runnerClassPath);
            container.withCopyToContainer(Transferable.of(pluginBytes), pluginClassPath);
            container.withCopyToContainer(Transferable.of(inputSer), WORK_DIR + "/input.ser");
            container.start();

            // Execute the plugin runner
            org.testcontainers.containers.Container.ExecResult execResult = container.execInContainer(
                    "java", "-cp", WORK_DIR,
                    PluginRunner.class.getName(),
                    className);

            String stdout = execResult.getStdout();
            String stderr = execResult.getStderr();
            int exitCode = execResult.getExitCode();

            System.out.println("=== [" + caseName + " | " + pluginName + " | JDK " + targetJdk + "] ===");
            System.out.println("STDOUT:\n" + stdout);
            if (!stderr.isEmpty()) {
                System.out.println("STDERR:\n" + stderr);
            }
            System.out.println("Exit code: " + exitCode);

            assertEquals(0, exitCode,
                    "Plugin " + pluginName + " failed on " + caseName + ".\nSTDERR: " + stderr);

            // Read output.ser from container
            byte[] outputBytes = copyFileFromContainer(container, WORK_DIR + "/output.ser");
            assertNotNull(outputBytes, "output.ser not found in container");
            assertTrue(outputBytes.length > 0, "output.ser is empty");

            @SuppressWarnings("unchecked")
            Map<String, Object> outputCtx = (Map<String, Object>) deserializeFromBytes(outputBytes);
            assertNotNull(outputCtx, "Failed to deserialize output.ser");

            // Verify result exists
            Object result = outputCtx.get("result");
            assertNotNull(result, "Plugin did not produce a result");

            // Plugins may return Map or List as result
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) result;
                assertNull(resultMap.get("error"),
                        "Plugin returned error: " + resultMap.get("error"));
            } else if (result instanceof List) {
                assertFalse(((List<?>) result).isEmpty(),
                        "Plugin returned empty list result");
            } else {
                fail("Unexpected result type: " + result.getClass().getName());
            }
        }
    }

    /**
     * Generates the cartesian product of JDK images × plugin test cases.
     */
    static Stream<Arguments> jdkPluginMatrix() throws Exception {
        List<JdkImage> images = loadJdkImages();
        String caseFilter = System.getProperty("noone.test.docker.caseFilter");

        List<Arguments> args = new ArrayList<>();
        for (JdkImage image : images) {
            if (caseFilter != null && !caseFilter.isEmpty() && !image.caseName.equals(caseFilter)) {
                continue;
            }
            for (PluginTestCase testCase : PLUGIN_TEST_CASES) {
                args.add(Arguments.of(
                        image.caseName,
                        testCase.pluginName,
                        image.targetJdk,
                        image.dockerImage,
                        testCase.pluginClass,
                        testCase.contextFactory.get()));
            }
        }
        return args.stream();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static Map<String, Object> commandExecutorContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("op", "exec");
        ctx.put("executable", "java");
        ctx.put("argv", new String[] { "-version" });
        ctx.put("cwd", "/tmp");
        return ctx;
    }

    private static byte[] serializeToBytes(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private static Object deserializeFromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return ois.readObject();
        }
    }

    private static byte[] copyFileFromContainer(GenericContainer<?> container, String containerPath) {
        try {
            return container.copyFileFromContainer(containerPath, inputStream -> {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                return baos.toByteArray();
            });
        } catch (Exception e) {
            fail("Failed to copy " + containerPath + " from container: " + e.getMessage());
            return null;
        }
    }

    // ── Image configuration parsing ─────────────────────────────────

    private static List<JdkImage> loadJdkImages() throws Exception {
        // Find jdk-matrix-images.txt relative to module root
        Path configFile = java.nio.file.Paths.get("docker", "jdk-matrix-images.txt");
        if (!Files.exists(configFile)) {
            // Try from project root
            configFile = java.nio.file.Paths.get("noone-plugins", "plugin-tests", "docker", "jdk-matrix-images.txt");
        }
        assertTrue(Files.exists(configFile), "Cannot find jdk-matrix-images.txt at " + configFile.toAbsolutePath());

        List<JdkImage> images = new ArrayList<>();
        for (String line : Files.readAllLines(configFile, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\|");
            if (parts.length >= 3) {
                images.add(new JdkImage(parts[0].trim(), Integer.parseInt(parts[1].trim()), parts[2].trim()));
            }
        }
        assertFalse(images.isEmpty(), "No JDK images found in " + configFile);
        return images;
    }

    // ── Data classes ────────────────────────────────────────────────

    private static class JdkImage {
        final String caseName;
        final int targetJdk;
        final String dockerImage;

        JdkImage(String caseName, int targetJdk, String dockerImage) {
            this.caseName = caseName;
            this.targetJdk = targetJdk;
            this.dockerImage = dockerImage;
        }
    }

    private static class PluginTestCase {
        final String pluginName;
        final Class<?> pluginClass;
        final ContextFactory contextFactory;

        PluginTestCase(String pluginName, Class<?> pluginClass, ContextFactory contextFactory) {
            this.pluginName = pluginName;
            this.pluginClass = pluginClass;
            this.contextFactory = contextFactory;
        }
    }

    @FunctionalInterface
    private interface ContextFactory {
        Map<String, Object> get();
    }
}
