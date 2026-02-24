package com.reajason.noone.plugin;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandExecutorTest {

    @SuppressWarnings("unchecked")
    @Test
    void shouldRequireOp() {
        CommandExecutor executor = new CommandExecutor();
        Map<String, Object> ctx = new HashMap<String, Object>();

        executor.equals(ctx);

        Map<String, Object> result = (Map<String, Object>) ctx.get("result");
        assertNotNull(result);
        assertEquals("op is required", result.get("error"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldChangeCwdForCdCommand() throws Exception {
        CommandExecutor executor = new CommandExecutor();
        Path baseDir = Files.createTempDirectory("command-executor-base");
        Path childDir = Files.createDirectories(baseDir.resolve("child"));

        Map<String, Object> ctx = new HashMap<String, Object>();
        ctx.put("op", "cd");
        ctx.put("cdTarget", "child");
        ctx.put("cwd", baseDir.toString());

        executor.equals(ctx);

        Map<String, Object> result = (Map<String, Object>) ctx.get("result");
        assertNotNull(result);
        assertEquals(0, ((Number) result.get("exitCode")).intValue());
        assertEquals(childDir.toFile().getCanonicalPath(), result.get("cwd"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnErrorForInvalidCdTarget() throws Exception {
        CommandExecutor executor = new CommandExecutor();
        Path baseDir = Files.createTempDirectory("command-executor-base");

        Map<String, Object> ctx = new HashMap<String, Object>();
        ctx.put("op", "cd");
        ctx.put("cdTarget", "not-exists");
        ctx.put("cwd", baseDir.toString());

        executor.equals(ctx);

        Map<String, Object> result = (Map<String, Object>) ctx.get("result");
        assertNotNull(result);
        assertTrue(String.valueOf(result.get("error")).contains("Directory does not exist"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldExecuteWithNormalizedArgs() throws Exception {
        CommandExecutor executor = new CommandExecutor();
        Path workingDir = Files.createTempDirectory("command-executor-run");

        Map<String, Object> ctx = new HashMap<String, Object>();
        ctx.put("op", "exec");
        ctx.put("executable", javaExecutable());
        ctx.put("argv", new String[]{"-version"});
        ctx.put("cwd", workingDir.toString());

        executor.equals(ctx);

        Map<String, Object> result = (Map<String, Object>) ctx.get("result");
        assertNotNull(result);
        assertNull(result.get("error"));
        assertEquals(0, ((Number) result.get("exitCode")).intValue());
        assertEquals(workingDir.toFile().getCanonicalPath(), result.get("cwd"));
        assertNotNull(result.get("charsetUsed"));
    }

    private String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String executableName = isWindows ? "java.exe" : "java";
        return new File(new File(javaHome, "bin"), executableName).getAbsolutePath();
    }
}
