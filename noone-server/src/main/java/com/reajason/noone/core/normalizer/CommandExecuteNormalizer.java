package com.reajason.noone.core.normalizer;

import com.reajason.noone.Constants;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandExecuteNormalizer implements PluginNormalizer {
    private static final String CMD_PLACEHOLDER = "{{cmd}}";
    private static final String CWD_PLACEHOLDER = "{{cwd}}";

    public Map<String, Object> normalizeArgs(Map<String, Object> args) {
        String cmd = asTrimString(args != null ? args.get("cmd") : null);
        if (cmd == null || cmd.isEmpty()) {
            return localFailure("cmd is required");
        }

        String cwd = asTrimString(args.get("cwd"));
        String charset = asTrimString(args.get("charset"));
        if (charset == null || charset.isEmpty()) {
            charset = "UTF-8";
        }

        String cdTarget = parseCdTarget(cmd);
        Map<String, Object> normalized = new HashMap<>();
        normalized.put("cwd", cwd == null ? "" : cwd);
        normalized.put("charset", charset);
        if (cdTarget != null) {
            normalized.put("op", "cd");
            normalized.put("cdTarget", cdTarget);
            return normalized;
        }

        Map<String, Object> template = toStringObjectMap(args.get("commandTemplate"));
        String executable = template == null ? null : renderTemplate(asTrimString(template.get("executable")), cmd, cwd);
        if (executable == null || executable.isEmpty()) {
            return localFailure("commandTemplate.executable is required");
        }
        normalized.put("op", "exec");
        normalized.put("executable", executable);
        normalized.put("argv", parseTemplateArgs(template.get("args"), cmd, cwd));
        normalized.put("env", parseTemplateEnv(template.get("env"), cmd, cwd));
        return normalized;
    }

    private String parseCdTarget(String cmd) {
        if (cmd == null) {
            return null;
        }
        String trimmed = cmd.trim();
        if (!trimmed.startsWith("cd")) {
            return null;
        }
        if (trimmed.length() > 2 && !Character.isWhitespace(trimmed.charAt(2))) {
            return null;
        }
        String rawTarget = trimmed.substring(2).trim();
        if (rawTarget.contains("&&") || rawTarget.contains("||") || rawTarget.contains(";") || rawTarget.contains("|")) {
            return null;
        }
        if (rawTarget.isEmpty()) {
            return "~";
        }
        return rawTarget;
    }

    private String renderTemplate(String template, String cmd, String cwd) {
        if (template == null) {
            return null;
        }
        String rendered = template.replace(CMD_PLACEHOLDER, cmd);
        return rendered.replace(CWD_PLACEHOLDER, cwd == null ? "" : cwd);
    }

    private List<String> parseTemplateArgs(Object rawArgs, String cmd, String cwd) {
        List<String> args = new ArrayList<>();
        if (rawArgs == null) {
            return args;
        }
        if (rawArgs instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) rawArgs) {
                if (item != null) {
                    args.add(renderTemplate(String.valueOf(item), cmd, cwd));
                }
            }
            return args;
        }
        Class<?> rawArgsClass = rawArgs.getClass();
        if (rawArgsClass.isArray()) {
            int length = Array.getLength(rawArgs);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(rawArgs, i);
                if (item != null) {
                    args.add(renderTemplate(String.valueOf(item), cmd, cwd));
                }
            }
            return args;
        }
        String single = asTrimString(rawArgs);
        if (single != null && !single.isEmpty()) {
            args.add(renderTemplate(single, cmd, cwd));
        }
        return args;
    }

    private Map<String, String> parseTemplateEnv(Object rawEnv, String cmd, String cwd) {
        Map<String, String> env = new HashMap<>();
        if (!(rawEnv instanceof Map<?, ?> map)) {
            return env;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = asTrimString(entry.getKey());
            if (key == null || key.isEmpty()) {
                continue;
            }
            env.put(key, renderTemplate(String.valueOf(entry.getValue()), cmd, cwd));
        }
        return env;
    }

    private Map<String, Object> localFailure(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.CODE, Constants.FAILURE);
        response.put(Constants.ERROR, message);
        return response;
    }

    private Map<String, Object> toStringObjectMap(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> copied = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            copied.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copied;
    }

    private String asTrimString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
