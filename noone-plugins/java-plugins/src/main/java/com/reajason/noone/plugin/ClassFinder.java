package com.reajason.noone.plugin;

import java.util.*;

/**
 * Class finder plugin.
 * Searches for a given class name across all classloaders in the target JVM.
 */
public class ClassFinder {

    @Override
    public boolean equals(Object obj) {
        Map<String, Object> map = (Map<String, Object>) obj;
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            String className = (String) map.get("className");
            if (className == null || className.trim().isEmpty()) {
                result.put("error", "className is required");
                map.put("result", result);
                return true;
            }

            Set<ClassLoader> visitedLoaders = new HashSet<ClassLoader>();
            List<Map<String, String>> found = new ArrayList<Map<String, String>>();

            // Collect classloaders from all threads
            Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
            for (Thread t : allThreads.keySet()) {
                try {
                    ClassLoader cl = t.getContextClassLoader();
                    collectClassLoaders(cl, visitedLoaders);
                } catch (Exception ignored) {
                }
            }

            // Also add system classloader and its parents
            try {
                collectClassLoaders(ClassLoader.getSystemClassLoader(), visitedLoaders);
            } catch (Exception ignored) {
            }

            // Also add current thread context classloader
            try {
                collectClassLoaders(Thread.currentThread().getContextClassLoader(), visitedLoaders);
            } catch (Exception ignored) {
            }

            // Try bootstrap / Class.forName
            try {
                Class.forName(className, false, null);
                Map<String, String> entry = new HashMap<String, String>();
                entry.put("className", className);
                entry.put("classLoaderType", "Bootstrap ClassLoader");
                entry.put("classLoaderIdentity", "null (Bootstrap)");
                found.add(entry);
            } catch (Exception ignored) {
            }

            // Try each collected classloader
            for (ClassLoader cl : visitedLoaders) {
                try {
                    Class<?> clazz = Class.forName(className, false, cl);
                    ClassLoader actualLoader = clazz.getClassLoader();
                    String loaderType = cl.getClass().getName();
                    String loaderIdentity = cl.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(cl));
                    if (actualLoader == null) {
                        loaderType = "Bootstrap ClassLoader (via " + cl.getClass().getName() + ")";
                        loaderIdentity = "null (Bootstrap) via " + cl.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(cl));
                    }
                    Map<String, String> entry = new HashMap<String, String>();
                    entry.put("className", clazz.getName());
                    entry.put("classLoaderType", loaderType);
                    entry.put("classLoaderIdentity", loaderIdentity);
                    found.add(entry);
                } catch (Exception ignored) {
                }
            }

            // Deduplicate by classLoaderIdentity
            Set<String> seen = new HashSet<String>();
            List<Map<String, String>> deduplicated = new ArrayList<Map<String, String>>();
            for (Map<String, String> entry : found) {
                String identity = entry.get("classLoaderIdentity");
                if (!seen.contains(identity)) {
                    seen.add(identity);
                    deduplicated.add(entry);
                }
            }

            // Format as text
            StringBuilder sb = new StringBuilder();
            if (deduplicated.isEmpty()) {
                sb.append("Class not found: ").append(className);
            } else {
                sb.append("Found class [").append(className).append("] in ").append(deduplicated.size()).append(" classloader(s):\n\n");
                for (int i = 0; i < deduplicated.size(); i++) {
                    Map<String, String> entry = deduplicated.get(i);
                    sb.append(i + 1).append(". ").append(entry.get("classLoaderType")).append("\n");
                    sb.append("   Identity: ").append(entry.get("classLoaderIdentity")).append("\n");
                    sb.append("   Class: ").append(entry.get("className")).append("\n\n");
                }
            }

            result.put("text", sb.toString());
        } catch (Exception e) {
            result.put("error", "Class finder failed: " + e.getMessage());
        }
        map.put("result", result);
        return true;
    }

    private static void collectClassLoaders(ClassLoader cl, Set<ClassLoader> visited) {
        while (cl != null) {
            if (visited.contains(cl)) {
                break;
            }
            visited.add(cl);
            cl = cl.getParent();
        }
    }
}
