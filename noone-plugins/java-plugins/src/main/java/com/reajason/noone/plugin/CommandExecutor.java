package com.reajason.noone.plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.Charset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command executor plugin.
 * This plugin only accepts normalized args from server-side ShellConnection.
 */
public class CommandExecutor {

    public CommandExecutor() {
    }

    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> ctx) {
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            String op = asTrimString(ctx.get("op"));
            if (op == null || op.isEmpty()) {
                throw new IllegalArgumentException("op is required");
            }

            String cwd = normalizeCwd(asTrimString(ctx.get("cwd")));
            String charsetName = asTrimString(ctx.get("charset"));
            Charset charset = resolveCharset(charsetName);
            result.put("cwd", cwd);
            result.put("charsetUsed", charset.name());

            if ("cd".equals(op)) {
                String cdTarget = asTrimString(ctx.get("cdTarget"));
                String nextCwd = resolveCdTarget(cwd, cdTarget);
                result.put("stdout", "");
                result.put("stderr", "");
                result.put("exitCode", 0);
                result.put("cwd", nextCwd);
                return result;
            }

            if (!"exec".equals(op)) {
                throw new IllegalArgumentException("unsupported op: " + op);
            }

            String executable = asTrimString(ctx.get("executable"));
            if (executable == null || executable.isEmpty()) {
                throw new IllegalArgumentException("executable is required");
            }

            List<String> command = new ArrayList<String>();
            command.add(executable);
            command.addAll(toStringList(ctx.get("argv")));

            ProcessBuilder pb = new ProcessBuilder(command);
            if (!cwd.isEmpty()) {
                pb.directory(new File(cwd));
            }
            pb.redirectErrorStream(false);
            applyEnv(pb.environment(), ctx.get("env"));

            Process process = pb.start();
            // Drain stdout and stderr while the process is running. Waiting for
            // exit before reading deadlocks as soon as the child fills the OS pipe
            // buffer, which makes the command appear to hang with no output.
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            int exitCode = collectOutput(process, stdout, stderr);

            result.put("stdout", new String(stdout.toByteArray(), charset));
            result.put("stderr", new String(stderr.toByteArray(), charset));
            result.put("exitCode", exitCode);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Command execution failed: " + safeMessage(e), e);
        }
        return result;
    }

    private static void applyEnv(Map<String, String> processEnv, Object rawEnv) {
        if (!(rawEnv instanceof Map)) {
            return;
        }
        Map<?, ?> envMap = (Map<?, ?>) rawEnv;
        for (Map.Entry<?, ?> entry : envMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = asTrimString(entry.getKey());
            if (key == null || key.isEmpty()) {
                continue;
            }
            processEnv.put(key, String.valueOf(entry.getValue()));
        }
    }

    private static List<String> toStringList(Object rawValue) {
        List<String> list = new ArrayList<String>();
        if (rawValue instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) rawValue) {
                if (item != null) {
                    list.add(String.valueOf(item));
                }
            }
        } else if (rawValue != null && rawValue.getClass().isArray()) {
            int length = Array.getLength(rawValue);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(rawValue, i);
                if (item != null) {
                    list.add(String.valueOf(item));
                }
            }
        } else if (rawValue != null) {
            list.add(String.valueOf(rawValue));
        }
        return list;
    }

    private static String resolveCdTarget(String currentCwd, String cdTarget) {
        String target = cdTarget == null ? "~" : stripPairQuote(cdTarget.trim());
        if (target.isEmpty() || "~".equals(target)) {
            return normalizeCwd(System.getProperty("user.home"));
        }
        if (target.startsWith("~/") || target.startsWith("~\\")) {
            target = normalizeCwd(System.getProperty("user.home")) + target.substring(1);
        }

        File targetDir = new File(target);
        if (!targetDir.isAbsolute()) {
            targetDir = new File(currentCwd, target);
        }
        String normalized = normalizePath(targetDir);
        File normalizedDir = new File(normalized);
        if (!normalizedDir.exists()) {
            throw new IllegalArgumentException("Directory does not exist: " + normalized);
        }
        if (!normalizedDir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + normalized);
        }
        return normalized;
    }

    private static String stripPairQuote(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String normalizeCwd(String rawCwd) {
        String cwd = rawCwd;
        if (cwd == null || cwd.isEmpty()) {
            cwd = System.getProperty("user.dir", ".");
        }
        File dir = new File(cwd);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir", "."), cwd);
        }
        return normalizePath(dir);
    }

    private static String normalizePath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception ignored) {
            return file.getAbsolutePath();
        }
    }

    private static Charset resolveCharset(String charsetName) {
        if (charsetName == null || charsetName.isEmpty()) {
            return Charset.forName("UTF-8");
        }
        try {
            return Charset.forName(charsetName);
        } catch (Exception ignored) {
            return Charset.forName("UTF-8");
        }
    }

    /**
     * Reads both output streams until the child process exits. Uses polling so
     * neither pipe can fill up while the other one is being read.
     */
    private static int collectOutput(Process process, ByteArrayOutputStream stdout,
                                     ByteArrayOutputStream stderr) throws Exception {
        InputStream out = process.getInputStream();
        InputStream err = process.getErrorStream();
        byte[] buffer = new byte[4096];
        while (true) {
            boolean readAny = copyAvailable(out, stdout, buffer);
            readAny = copyAvailable(err, stderr, buffer) || readAny;
            try {
                int exitCode = process.exitValue();
                drain(out, stdout, buffer);
                drain(err, stderr, buffer);
                return exitCode;
            } catch (IllegalThreadStateException running) {
                if (!readAny) {
                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private static boolean copyAvailable(InputStream in, ByteArrayOutputStream out, byte[] buffer)
            throws Exception {
        int available = in.available();
        if (available <= 0) {
            return false;
        }
        int toRead = available < buffer.length ? available : buffer.length;
        int read = in.read(buffer, 0, toRead);
        if (read <= 0) {
            return false;
        }
        out.write(buffer, 0, read);
        return true;
    }

    private static void drain(InputStream in, ByteArrayOutputStream out, byte[] buffer) throws Exception {
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
    }

    private static String asTrimString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }
}
