package com.reajason.noone.test.compatibility.dotnet;

import com.alibaba.fastjson2.JSONObject;
import com.reajason.noone.core.DotNetConnection;
import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.client.HttpClient;
import com.reajason.noone.core.client.HttpClientConfig;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.test.compatibility.ProfileFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real-environment .NET integration test.
 * <p>
 * Publishes and starts {@code noone-test/dotnet-server}, a minimal ASP.NET-free
 * HTTP host that loads the real {@code dotnet-core.dll}, then connects with
 * {@link DotNetConnection} and exercises the system-info and command-execute plugins.
 */
@Slf4j
public class DotNetCompatibilityTest {

    private static Process serverProcess;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        assumeTrue(isDotnetAvailable(), "dotnet SDK is not available");

        Path moduleDir = Paths.get("").toAbsolutePath();
        Path hostProject = moduleDir.resolve("..").resolve("dotnet-server").normalize();
        Path publishDir = hostProject.resolve("bin").resolve("publish");
        Path coreDll = moduleDir.resolve("..").resolve("..")
                .resolve("noone-core").resolve("src").resolve("main").resolve("resources")
                .resolve("dotnet-core.dll").normalize();

        assertTrue(Files.isDirectory(hostProject), "dotnet-server project not found: " + hostProject);
        assertTrue(Files.exists(coreDll), "dotnet-core.dll not found: " + coreDll);

        publish(hostProject, publishDir);

        port = findFreePort();
        Path serverDll = publishDir.resolve("dotnet-server.dll");
        serverProcess = new ProcessBuilder(
                "dotnet", serverDll.toString(), coreDll.toString(), String.valueOf(port))
                .redirectErrorStream(true)
                .start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8));
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);
        String line;
        while (System.currentTimeMillis() < deadline) {
            line = reader.readLine();
            if (line == null) {
                break;
            }
            log.info("dotnet-server: {}", line);
            if (line.contains("dotnet shell host listening on")) {
                drainInBackground(reader);
                return;
            }
        }
        if (serverProcess != null) {
            serverProcess.destroyForcibly();
        }
        fail("dotnet-server did not start listening on port " + port);
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (serverProcess != null) {
            serverProcess.destroy();
            if (!serverProcess.waitFor(10, TimeUnit.SECONDS)) {
                serverProcess.destroyForcibly();
            }
        }
    }

    @Test
    void test() throws IOException {
        String url = "http://127.0.0.1:" + port;
        Profile coreProfile = ProfileFactory.base64Json();
        HttpClient coreClient = new HttpClient(url, HttpClientConfig.builder()
                .requestHeaders(Map.of("No-One-Version", "V1"))
                .contentType("application/json; charset=utf-8")
                .build());

        DotNetConnection conn = new DotNetConnection(coreClient, coreProfile);
        assertTrue(conn.test(), ".NET shell status check failed");

        Map<String, Object> systemInfo = runPlugin(conn, "dotnet", "system-info", new HashMap<>());
        assertSuccess(systemInfo, "system-info");
        Map<String, Object> systemInfoData = assertInstanceOf(Map.class,
                systemInfo.get("data"), "system-info data should be a map");
        log.info("system-info: {}", systemInfoData.keySet());
        assertNotNull(systemInfoData.get("os"), "system-info should report os details");
        assertNotNull(systemInfoData.get("runtime"), "system-info should report runtime details");

        Map<String, Object> commandArgs = new HashMap<>();
        commandArgs.put("cmd", "no-one-integration");
        Map<String, Object> commandTemplate = new HashMap<>();
        commandTemplate.put("executable", "/bin/echo");
        commandTemplate.put("args", List.of("{{cmd}}"));
        commandArgs.put("commandTemplate", commandTemplate);

        Map<String, Object> commandResult = runPlugin(conn, "dotnet", "command-execute", commandArgs);
        assertSuccess(commandResult, "command-execute");
        Map<String, Object> commandData = assertInstanceOf(Map.class,
                commandResult.get("data"), "command-execute data should be a map");
        log.info("command-execute: {}", commandData);
        assertEquals(0, ((Number) commandData.get("exitCode")).intValue(), "command should exit cleanly");
        assertEquals("no-one-integration", String.valueOf(commandData.get("stdout")).trim());
    }

    private static Map<String, Object> runPlugin(ShellConnection conn, String language,
                                                 String pluginId, Map<String, Object> args)
            throws IOException {
        Path releaseFile = Paths.get("..", "..", "noone-plugins", "release", language,
                language + "-" + pluginId + "-plugin-0.0.1.json");
        JSONObject metadata = JSONObject.parseObject(Files.readString(releaseFile, StandardCharsets.UTF_8));
        conn.loadPlugin(metadata.getString("id"), metadata.getString("version"),
                Base64.getDecoder().decode(metadata.getString("payload")));
        return conn.runPlugin(metadata.getString("id"), args);
    }

    private static void assertSuccess(Map<String, Object> response, String pluginId) {
        assertNotNull(response, pluginId + " returned no response");
        Object code = response.get("code");
        assertInstanceOf(Number.class, code, pluginId + " response has no code");
        assertEquals(0, ((Number) code).intValue(), pluginId + " failed: " + response.get("error"));
    }

    private static void publish(Path hostProject, Path publishDir) throws Exception {
        Process process = new ProcessBuilder(
                "dotnet", "publish", hostProject.toString(),
                "-c", "Release", "-o", publishDir.toString(), "--nologo")
                .redirectErrorStream(true)
                .start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            fail("dotnet publish timed out for " + hostProject);
        }
        assertEquals(0, process.exitValue(), "dotnet publish failed:\n" + output);
    }

    private static boolean isDotnetAvailable() {
        try {
            Process process = new ProcessBuilder("dotnet", "--version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void drainInBackground(BufferedReader reader) {
        Thread thread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("dotnet-server: {}", line);
                }
            } catch (IOException ignored) {
                // process stopped
            }
        }, "dotnet-server-output");
        thread.setDaemon(true);
        thread.start();
    }
}
