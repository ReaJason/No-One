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
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Docker-based .NET runtime matrix test for plugin compatibility.
 * <p>
 * Verifies that the .NET plugin DLL (targeting netstandard2.0) can be loaded
 * and executed via the dotnet-runner in Docker containers running various
 * .NET runtime versions.
 * <p>
 * Prerequisites: Run {@code dotnet publish} for dotnet-runner before this test.
 * <p>
 * Run via: ./gradlew :noone-plugins:plugin-tests:dockerDotnetMatrixTest
 */
@Tag("docker-dotnet-matrix")
class DockerDotnetMatrixTest {

    private static final String WORK_DIR = "/work";

    /**
     * Plugin test case: class name and input context.
     * Currently only dotnet-system-info is available.
     */
    private static final List<PluginTestCase> PLUGIN_TEST_CASES = Arrays.asList(
            new PluginTestCase(
                    "dotnet-system-info.dll",
                    "NoOne.Plugins.Dotnet.SystemInfo.DotnetSystemInfoPlugin",
                    Collections.emptyMap()));

    private static Path publishDir;

    @BeforeAll
    static void locatePublishDir() {
        publishDir = resolveDotnetRunnerPublishDir();
        assertTrue(Files.isDirectory(publishDir),
                "dotnet-runner publish dir not found at " + publishDir.toAbsolutePath()
                        + ". Run: cd noone-plugins/dotnet-runner && dotnet publish -c Release -o bin/publish");
        assertTrue(Files.exists(publishDir.resolve("dotnet-runner.dll")),
                "dotnet-runner.dll not found in " + publishDir.toAbsolutePath());
    }

    @ParameterizedTest(name = "{0} | {1} | .NET {2}")
    @MethodSource("dotnetPluginMatrix")
    void pluginShouldLoadAndRunOnDotnet(String caseName, String pluginDll,
            int targetDotnet, String dockerImage,
            String className,
            String inputContextJson) throws Exception {

        try (GenericContainer<?> container = new GenericContainer<>(dockerImage)) {
            container.withCreateContainerCmdModifier(
                    cmd -> cmd.withEntrypoint("sleep").withCmd("infinity"));

            // Copy all published files into the container
            copyDirectoryToContainer(container, publishDir, WORK_DIR);

            // Write input.json
            byte[] inputBytes = inputContextJson.getBytes(StandardCharsets.UTF_8);
            container.withCopyToContainer(Transferable.of(inputBytes), WORK_DIR + "/input.json");

            container.start();

            // Execute: dotnet dotnet-runner.dll <plugin.dll> <className> input.json
            // output.json
            org.testcontainers.containers.Container.ExecResult execResult = container.execInContainer(
                    "dotnet", WORK_DIR + "/dotnet-runner.dll",
                    WORK_DIR + "/" + pluginDll,
                    className,
                    WORK_DIR + "/input.json",
                    WORK_DIR + "/output.json");

            String stdout = execResult.getStdout();
            String stderr = execResult.getStderr();
            int exitCode = execResult.getExitCode();

            System.out.println("=== [" + caseName + " | " + pluginDll + " | .NET " + targetDotnet + "] ===");
            System.out.println("STDOUT:\n" + stdout);
            if (stderr != null && !stderr.isEmpty()) {
                System.out.println("STDERR:\n" + stderr);
            }
            System.out.println("Exit code: " + exitCode);

            assertEquals(0, exitCode,
                    "Plugin " + pluginDll + " failed on " + caseName + ".\nSTDERR: " + stderr);

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
     * Generates the cartesian product of .NET images × plugin test cases.
     */
    static Stream<Arguments> dotnetPluginMatrix() throws Exception {
        List<DotnetImage> images = loadDotnetImages();
        String caseFilter = System.getProperty("noone.test.docker.dotnet.caseFilter");

        List<Arguments> args = new ArrayList<>();
        for (DotnetImage image : images) {
            if (caseFilter != null && !caseFilter.isEmpty() && !image.caseName.equals(caseFilter)) {
                continue;
            }
            for (PluginTestCase testCase : PLUGIN_TEST_CASES) {
                String inputJson = JSON.toJSONString(testCase.inputContext);
                args.add(Arguments.of(
                        image.caseName,
                        testCase.pluginDll,
                        image.targetDotnet,
                        image.dockerImage,
                        testCase.className,
                        inputJson));
            }
        }
        return args.stream();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static void copyDirectoryToContainer(GenericContainer<?> container, Path dir, String containerDir)
            throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String targetPath = containerDir + "/" + entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    copyDirectoryToContainer(container, entry, targetPath);
                } else {
                    byte[] bytes = Files.readAllBytes(entry);
                    container.withCopyToContainer(Transferable.of(bytes), targetPath);
                }
            }
        }
    }

    private static Path resolveDotnetRunnerPublishDir() {
        Path candidate = Paths.get("../dotnet-runner/bin/publish");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        candidate = Paths.get("noone-plugins", "dotnet-runner", "bin", "publish");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        return candidate;
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

    private static List<DotnetImage> loadDotnetImages() throws Exception {
        Path configFile = Paths.get("docker", "dotnet-matrix-images.txt");
        if (!Files.exists(configFile)) {
            configFile = Paths.get("noone-plugins", "plugin-tests", "docker", "dotnet-matrix-images.txt");
        }
        assertTrue(Files.exists(configFile),
                "Cannot find dotnet-matrix-images.txt at " + configFile.toAbsolutePath());

        List<DotnetImage> images = new ArrayList<>();
        for (String line : Files.readAllLines(configFile, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\|");
            if (parts.length >= 3) {
                images.add(new DotnetImage(parts[0].trim(), Integer.parseInt(parts[1].trim()), parts[2].trim()));
            }
        }
        assertFalse(images.isEmpty(), "No .NET images found in " + configFile);
        return images;
    }

    // ── Data classes ────────────────────────────────────────────────

    private static class DotnetImage {
        final String caseName;
        final int targetDotnet;
        final String dockerImage;

        DotnetImage(String caseName, int targetDotnet, String dockerImage) {
            this.caseName = caseName;
            this.targetDotnet = targetDotnet;
            this.dockerImage = dockerImage;
        }
    }

    private static class PluginTestCase {
        final String pluginDll;
        final String className;
        final Map<String, Object> inputContext;

        PluginTestCase(String pluginDll, String className, Map<String, Object> inputContext) {
            this.pluginDll = pluginDll;
            this.className = className;
            this.inputContext = inputContext;
        }
    }
}
