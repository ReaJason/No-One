package com.reajason.noone.plugin;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal plugin runner for JDK compatibility verification.
 * All APIs used here are available since JDK 1.6.
 * <p>
 * Usage: java -cp /work com.reajason.noone.plugin.PluginRunner
 * &lt;className&gt;
 * <p>
 * Expects:
 * - /work/input.ser : serialized Map&lt;String,Object&gt; (plugin context, may
 * be absent for simple load test)
 * - /work/&lt;className-as-path&gt;.class : plugin class bytecode
 * <p>
 * Produces:
 * - /work/output.ser : serialized Map&lt;String,Object&gt; (ctx after
 * plugin.equals(ctx))
 * - exit code 0 on success, 1 on failure
 */
public class PluginRunner extends ClassLoader {

    private static final String WORK_DIR = "/work";

    public PluginRunner(ClassLoader parent) {
        super(parent);
    }

    public Class<?> defineClass(String name, byte[] bytes) {
        return super.defineClass(name, bytes, 0, bytes.length);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: PluginRunner <className>");
            System.exit(1);
        }
        String className = args[0];
        try {
            // 1. Read plugin class bytecode
            String classFilePath = WORK_DIR + "/" + className.replace('.', '/') + ".class";
            byte[] classBytes = readAllBytes(classFilePath);
            System.out.println(
                    "[PluginRunner] Loaded class bytes: " + classBytes.length + " bytes from " + classFilePath);

            // 2. defineClass + newInstance
            PluginRunner loader = new PluginRunner(PluginRunner.class.getClassLoader());
            Class<?> pluginClass = loader.defineClass(className, classBytes);
            Object plugin = pluginClass.newInstance();
            System.out.println("[PluginRunner] Plugin instantiated: " + pluginClass.getName());

            // 3. Prepare context map
            Map<String, Object> ctx = loadInputContext();
            System.out.println("[PluginRunner] Input context keys: " + ctx.keySet());

            // 4. Invoke plugin via equals(Map)
            plugin.equals(ctx);
            System.out.println("[PluginRunner] Plugin executed successfully");

            // 5. Serialize output (full ctx including "result")
            writeOutput(ctx);
            System.out.println("[PluginRunner] Output written to output.ser");
            System.out.println("[PluginRunner] Result keys: " +
                    (ctx.get("result") instanceof Map ? ((Map<?, ?>) ctx.get("result")).keySet() : ctx.get("result")));

            System.exit(0);
        } catch (Throwable t) {
            System.err.println("[PluginRunner] FAILED: " + t.getMessage());
            t.printStackTrace(System.err);
            // Write error info to output.ser so the test can inspect it
            try {
                Map<String, Object> errorCtx = new HashMap<String, Object>();
                Map<String, Object> errorResult = new HashMap<String, Object>();
                errorResult.put("error", t.getClass().getName() + ": " + t.getMessage());
                errorCtx.put("result", errorResult);
                writeOutput(errorCtx);
            } catch (Exception ignored) {
            }
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadInputContext() {
        File inputFile = new File(WORK_DIR, "input.ser");
        if (!inputFile.exists()) {
            // No input context provided, use empty map (simple load+run test)
            return new HashMap<String, Object>();
        }
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(new FileInputStream(inputFile));
            return (Map<String, Object>) ois.readObject();
        } catch (Exception e) {
            System.err.println(
                    "[PluginRunner] Warning: failed to read input.ser, using empty context: " + e.getMessage());
            return new HashMap<String, Object>();
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void writeOutput(Map<String, Object> ctx) throws IOException {
        // Deep-copy and sanitize to ensure all values are Serializable
        HashMap<String, Object> sanitized = sanitizeMap(ctx);
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(new FileOutputStream(new File(WORK_DIR, "output.ser")));
            oos.writeObject(sanitized);
            oos.flush();
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static HashMap<String, Object> sanitizeMap(Map<String, Object> map) {
        HashMap<String, Object> result = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return result;
    }

    private static Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapValue = (Map<String, Object>) value;
            return sanitizeMap(mapValue);
        }
        if (value instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) value;
            java.util.ArrayList<Object> sanitizedList = new java.util.ArrayList<Object>();
            for (Object item : list) {
                sanitizedList.add(sanitizeValue(item));
            }
            return sanitizedList;
        }
        if (value instanceof Serializable) {
            return value;
        }
        // Fallback: convert non-serializable to String
        return value.toString();
    }

    private static byte[] readAllBytes(String filePath) throws IOException {
        FileInputStream fis = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            fis = new FileInputStream(filePath);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }
        return baos.toByteArray();
    }
}
