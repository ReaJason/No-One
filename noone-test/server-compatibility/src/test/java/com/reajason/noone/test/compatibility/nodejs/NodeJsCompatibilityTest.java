package com.reajason.noone.test.compatibility.nodejs;

import com.alibaba.fastjson2.JSONObject;
import com.reajason.noone.core.NodeJsConnection;
import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.client.HttpClient;
import com.reajason.noone.core.client.HttpClientConfig;
import com.reajason.noone.core.generator.NoOneNodeJsWebShellGenerator;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.test.compatibility.ProfileFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * Real-environment Node.js integration test.
 * <p>
 * Generates a Node.js web shell with the production generator, serves it with a real
 * Node.js runtime, then connects with {@link NodeJsConnection} and exercises the
 * system-info and command-execute plugins.
 * <p>
 * By default the shell runs inside a {@code node:20-slim} container. Set
 * {@code NO_ONE_TEST_NODE_LOCAL=true} to run it with the local {@code node} binary
 * instead (useful when the image cannot be pulled).
 */
@Slf4j
public class NodeJsCompatibilityTest {

    private static final boolean USE_LOCAL_NODE =
            Boolean.parseBoolean(System.getenv().getOrDefault("NO_ONE_TEST_NODE_LOCAL", "false"));

    /**
     * Minimal host application. The generated shell patches
     * {@code http.Server.prototype.emit}, so it only needs any listening server.
     */
    private static final String HOST_JS = """
            const http = await import('node:http');
            const fs = await import('node:fs');
            await new Function('return ' + fs.readFileSync(new URL('./shell.mjs', import.meta.url), 'utf8'))();
            const port = Number(process.argv[2] || 3000);
            http.createServer((req, res) => {
                res.writeHead(200, { 'Content-Type': 'text/plain' });
                res.end('ok');
            }).listen(port, () => console.log('node shell host listening on ' + port));
            """;

    private static final String SHELL_JS = generateShell();

    private static GenericContainer<?> container;
    private static Process localProcess;
    private static String url;

    @BeforeAll
    static void startShell() throws Exception {
        if (USE_LOCAL_NODE) {
            startLocalNode();
        } else {
            startContainer();
        }
    }

    @AfterAll
    static void stopShell() throws Exception {
        if (container != null) {
            container.stop();
        }
        if (localProcess != null) {
            localProcess.destroy();
            if (!localProcess.waitFor(10, TimeUnit.SECONDS)) {
                localProcess.destroyForcibly();
            }
        }
    }

    private static void startContainer() {
        container = new GenericContainer<>("node:20-slim")
                .withCopyToContainer(Transferable.of(SHELL_JS.getBytes(StandardCharsets.UTF_8)), "/app/shell.mjs")
                .withCopyToContainer(Transferable.of(HOST_JS.getBytes(StandardCharsets.UTF_8)), "/app/host.mjs")
                .withCommand("node", "/app/host.mjs", "3000")
                .waitingFor(Wait.forLogMessage(".*node shell host listening.*", 1))
                .withExposedPorts(3000);
        container.start();
        url = "http://" + container.getHost() + ":" + container.getMappedPort(3000);
    }

    @SneakyThrows
    private static void startLocalNode() {
        assumeTrue(isNodeAvailable(), "node is not available");
        Path dir = Files.createTempDirectory("no-one-node-shell");
        Files.writeString(dir.resolve("shell.mjs"), SHELL_JS, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("host.mjs"), HOST_JS, StandardCharsets.UTF_8);

        int port = findFreePort();
        localProcess = new ProcessBuilder("node", dir.resolve("host.mjs").toString(), String.valueOf(port))
                .redirectErrorStream(true)
                .start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(localProcess.getInputStream(), StandardCharsets.UTF_8));
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);
        while (System.currentTimeMillis() < deadline) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            log.info("node-shell: {}", line);
            if (line.contains("node shell host listening on")) {
                drainInBackground(reader);
                url = "http://127.0.0.1:" + port;
                return;
            }
        }
        localProcess.destroyForcibly();
        fail("local node shell did not start");
    }

    private static String generateShell() {
        NoOneConfig config = NoOneConfig.builder()
                .coreProfile(ProfileFactory.base64Json())
                .build();
        return new NoOneNodeJsWebShellGenerator(config).generateMjs();
    }

    @Test
    @SneakyThrows
    void test() {
        Profile coreProfile = ProfileFactory.base64Json();
        HttpClient coreClient = new HttpClient(url, HttpClientConfig.builder()
                .requestHeaders(Map.of("No-One-Version", "V1"))
                .contentType("application/json; charset=utf-8")
                .build());

        NodeJsConnection conn = new NodeJsConnection(coreClient, coreProfile);
        assertTrue(conn.test(), "Node.js shell status check failed");

        Map<String, Object> systemInfo = runPlugin(conn, "nodejs", "system-info", new HashMap<>());
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

        Map<String, Object> commandResult = runPlugin(conn, "nodejs", "command-execute", commandArgs);
        assertSuccess(commandResult, "command-execute");
        Map<String, Object> commandData = assertInstanceOf(Map.class,
                commandResult.get("data"), "command-execute data should be a map");
        log.info("command-execute: {}", commandData);
        assertEquals(0, ((Number) commandData.get("exitCode")).intValue(), "command should exit cleanly");
        assertEquals("no-one-integration", String.valueOf(commandData.get("stdout")).trim());
    }

    @SneakyThrows
    private static Map<String, Object> runPlugin(ShellConnection conn, String language,
                                                 String pluginId, Map<String, Object> args) {
        Path releaseFile = Paths.get("..", "..", "noone-plugins", "release", language,
                language + "-" + pluginId + "-plugin-0.0.1.json");
        JSONObject metadata = JSONObject.parseObject(Files.readString(releaseFile, StandardCharsets.UTF_8));
        conn.loadPlugin(metadata.getString("id"), metadata.getString("version"),
                metadata.getString("payload").getBytes(StandardCharsets.UTF_8));
        return conn.runPlugin(metadata.getString("id"), args);
    }

    private static void assertSuccess(Map<String, Object> response, String pluginId) {
        assertNotNull(response, pluginId + " returned no response");
        Object code = response.get("code");
        assertInstanceOf(Number.class, code, pluginId + " response has no code");
        assertEquals(0, ((Number) code).intValue(), pluginId + " failed: " + response.get("error"));
    }

    private static boolean isNodeAvailable() {
        try {
            Process process = new ProcessBuilder("node", "--version")
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
                    log.info("node-shell: {}", line);
                }
            } catch (IOException ignored) {
                // process stopped
            }
        }, "node-shell-output");
        thread.setDaemon(true);
        thread.start();
    }
}
