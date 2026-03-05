package com.reajason.noone.plugin;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Docker-based Node.js matrix test for plugin compatibility.
 * <p>
 * Verifies that each Node.js plugin (.mjs) can be loaded and executed via
 * {@code runner.mjs} inside Docker containers running various Node.js LTS
 * versions.
 * <p>
 * Run via: ./gradlew :noone-plugins:plugin-tests:dockerNodeMatrixTest
 */
@Tag("docker-node-matrix")
class DockerNodeMatrixTest {

    private static final String WORK_DIR = "/work";

    private static String runnerSource;

    /**
     * Plugin test case definitions. Each entry specifies the plugin filename
     * and the input context (as a JSON-serializable Map).
     */
    private static final List<PluginTestCase> PLUGIN_TEST_CASES = Arrays.asList(
            new PluginTestCase("system-info.mjs", Collections.emptyMap()),
            new PluginTestCase("command-execute.mjs", commandExecuteContext()),
            new PluginTestCase("file-manager.mjs", fileManagerContext()));

    @BeforeAll
    static void loadRunner() throws IOException {
        Path runnerPath = resolveNodejsPluginsDir().resolve("runner.mjs");
        assertTrue(Files.exists(runnerPath), "runner.mjs not found at " + runnerPath.toAbsolutePath());
        runnerSource = new String(Files.readAllBytes(runnerPath), StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "{0} | {1} | Node {2}")
    @MethodSource("nodePluginMatrix")
    void pluginShouldLoadAndRunOnNode(String caseName, String pluginName,
            int targetNode, String dockerImage,
            String pluginSource,
            String inputContextJson) throws Exception {

        byte[] runnerBytes = runnerSource.getBytes(StandardCharsets.UTF_8);
        byte[] pluginBytes = pluginSource.getBytes(StandardCharsets.UTF_8);
        byte[] inputBytes = inputContextJson.getBytes(StandardCharsets.UTF_8);

        try (GenericContainer<?> container = new GenericContainer<>(dockerImage)) {
            container.withCreateContainerCmdModifier(
                    cmd -> cmd.withEntrypoint("sleep").withCmd("infinity"));
            container.withCopyToContainer(Transferable.of(runnerBytes), WORK_DIR + "/runner.mjs");
            container.withCopyToContainer(Transferable.of(pluginBytes), WORK_DIR + "/plugin.mjs");
            container.withCopyToContainer(Transferable.of(inputBytes), WORK_DIR + "/input.json");
            container.start();

            // Execute: node runner.mjs plugin.mjs input.json output.json
            org.testcontainers.containers.Container.ExecResult execResult = container.execInContainer(
                    "node", WORK_DIR + "/runner.mjs",
                    WORK_DIR + "/plugin.mjs",
                    WORK_DIR + "/input.json",
                    WORK_DIR + "/output.json");

            String stdout = execResult.getStdout();
            String stderr = execResult.getStderr();
            int exitCode = execResult.getExitCode();

            System.out.println("=== [" + caseName + " | " + pluginName + " | Node " + targetNode + "] ===");
            System.out.println("STDOUT:\n" + stdout);
            if (stderr != null && !stderr.isEmpty()) {
                System.out.println("STDERR:\n" + stderr);
            }
            System.out.println("Exit code: " + exitCode);

            assertEquals(0, exitCode,
                    "Plugin " + pluginName + " failed on " + caseName + ".\nSTDERR: " + stderr);

            // Read output.json from container
            byte[] outputBytes = copyFileFromContainer(container, WORK_DIR + "/output.json");
            assertNotNull(outputBytes, "output.json not found in container");
            assertTrue(outputBytes.length > 0, "output.json is empty");

            String outputJson = new String(outputBytes, StandardCharsets.UTF_8);
            JSONObject result = JSON.parseObject(outputJson);
            assertNotNull(result, "Failed to parse output.json");
            assertNull(result.getString("error"),
                    "Plugin returned error: " + result.getString("error"));

            System.out.println("Result keys: " + result.keySet());
        }
    }

    /**
     * Generates the cartesian product of Node.js images × plugin test cases.
     */
    static Stream<Arguments> nodePluginMatrix() throws Exception {
        List<NodeImage> images = loadNodeImages();
        String caseFilter = System.getProperty("noone.test.docker.node.caseFilter");

        Path pluginsDir = resolveNodejsPluginsDir();

        List<Arguments> args = new ArrayList<>();
        for (NodeImage image : images) {
            if (caseFilter != null && !caseFilter.isEmpty() && !image.caseName.equals(caseFilter)) {
                continue;
            }
            for (PluginTestCase testCase : PLUGIN_TEST_CASES) {
                Path pluginPath = pluginsDir.resolve(testCase.pluginFilename);
                assertTrue(Files.exists(pluginPath),
                        testCase.pluginFilename + " not found at " + pluginPath.toAbsolutePath());
                String pluginSource = new String(Files.readAllBytes(pluginPath), StandardCharsets.UTF_8);
                String inputJson = JSON.toJSONString(testCase.inputContext);

                args.add(Arguments.of(
                        image.caseName,
                        testCase.pluginFilename,
                        image.targetNode,
                        image.dockerImage,
                        pluginSource,
                        inputJson));
            }
        }
        return args.stream();
    }

    // ── Context factories ────────────────────────────────────────────

    private static Map<String, Object> commandExecuteContext() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("op", "exec");
        ctx.put("executable", "node");
        ctx.put("argv", new String[] { "--version" });
        ctx.put("cwd", "/tmp");
        return ctx;
    }

    private static Map<String, Object> fileManagerContext() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("op", "list");
        ctx.put("path", "/tmp");
        return ctx;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static Path resolveNodejsPluginsDir() {
        // Try relative to module root (when running from plugin-tests/)
        Path candidate = Paths.get("../nodejs-plugins");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        // Try from project root
        candidate = Paths.get("noone-plugins", "nodejs-plugins");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        throw new IllegalStateException(
                "Cannot locate nodejs-plugins directory. CWD=" + Paths.get("").toAbsolutePath());
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

    private static List<NodeImage> loadNodeImages() throws Exception {
        Path configFile = Paths.get("docker", "node-matrix-images.txt");
        if (!Files.exists(configFile)) {
            configFile = Paths.get("noone-plugins", "plugin-tests", "docker", "node-matrix-images.txt");
        }
        assertTrue(Files.exists(configFile),
                "Cannot find node-matrix-images.txt at " + configFile.toAbsolutePath());

        List<NodeImage> images = new ArrayList<>();
        for (String line : Files.readAllLines(configFile, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\|");
            if (parts.length >= 3) {
                images.add(new NodeImage(parts[0].trim(), Integer.parseInt(parts[1].trim()), parts[2].trim()));
            }
        }
        assertFalse(images.isEmpty(), "No Node.js images found in " + configFile);
        return images;
    }

    // ── Data classes ────────────────────────────────────────────────

    private static class NodeImage {
        final String caseName;
        final int targetNode;
        final String dockerImage;

        NodeImage(String caseName, int targetNode, String dockerImage) {
            this.caseName = caseName;
            this.targetNode = targetNode;
            this.dockerImage = dockerImage;
        }
    }

    private static class PluginTestCase {
        final String pluginFilename;
        final Map<String, Object> inputContext;

        PluginTestCase(String pluginFilename, Map<String, Object> inputContext) {
            this.pluginFilename = pluginFilename;
            this.inputContext = inputContext;
        }
    }
}
