package com.reajason.noone.plugin;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandExecutorTest {

    @Test
    void shouldRequireOp() {
        CommandExecutor executor = new CommandExecutor();
        Map<String, Object> ctx = new HashMap<String, Object>();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> executor.execute(ctx));
        assertEquals("op is required", error.getMessage());
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

        Map<String, Object> result = (Map<String, Object>) executor.execute(ctx);
        assertNotNull(result);
        assertEquals(0, ((Number) result.get("exitCode")).intValue());
        assertEquals(childDir.toFile().getCanonicalPath(), result.get("cwd"));
    }

    @Test
    void shouldReturnErrorForInvalidCdTarget() throws Exception {
        CommandExecutor executor = new CommandExecutor();
        Path baseDir = Files.createTempDirectory("command-executor-base");

        Map<String, Object> ctx = new HashMap<String, Object>();
        ctx.put("op", "cd");
        ctx.put("cdTarget", "not-exists");
        ctx.put("cwd", baseDir.toString());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> executor.execute(ctx));
        assertTrue(error.getMessage().contains("Directory does not exist"));
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

        Map<String, Object> result = (Map<String, Object>) executor.execute(ctx);
        assertNotNull(result);
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
