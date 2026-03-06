package com.reajason.noone.plugin;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;

/**
 * Port scanner plugin. Designed for async execution via TaskManager.
 * Scans a target host for open ports within a given range.
 * Updates ctx result incrementally so partial results are available during execution.
 *
 * @author ReaJason
 * @since 2026/3/6
 */
public class PortScanner {

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Map)) {
            return true;
        }
        Map<String, Object> ctx = (Map<String, Object>) obj;
        Map<String, Object> result = new HashMap<String, Object>();
        ctx.put("result", result);

        String target = asString(ctx.get("target"));
        if (target == null || target.isEmpty()) {
            result.put("error", "target is required");
            return true;
        }

        String portsStr = asString(ctx.get("ports"));
        if (portsStr == null || portsStr.isEmpty()) {
            portsStr = "1-1024";
        }

        int timeout = asInt(ctx.get("timeout"), 200);

        int[] range = parsePortRange(portsStr);
        if (range == null) {
            result.put("error", "invalid port range: " + portsStr);
            return true;
        }

        int startPort = range[0];
        int endPort = range[1];
        int totalPorts = endPort - startPort + 1;

        List<Object> openPorts = new ArrayList<Object>();
        result.put("target", target);
        result.put("portRange", portsStr);
        result.put("openPorts", openPorts);
        result.put("scanned", Integer.valueOf(0));
        result.put("total", Integer.valueOf(totalPorts));
        result.put("status", "scanning");

        for (int port = startPort; port <= endPort; port++) {
            if (Thread.currentThread().isInterrupted()) {
                result.put("status", "cancelled");
                return true;
            }

            if (isPortOpen(target, port, timeout)) {
                openPorts.add(Integer.valueOf(port));
            }

            result.put("scanned", Integer.valueOf(port - startPort + 1));
        }

        result.put("status", "completed");
        return true;
    }

    private boolean isPortOpen(String host, int port, int timeoutMs) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private int[] parsePortRange(String portsStr) {
        portsStr = portsStr.trim();
        int dashIdx = portsStr.indexOf('-');
        if (dashIdx < 0) {
            try {
                int p = Integer.parseInt(portsStr);
                return new int[]{p, p};
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            int start = Integer.parseInt(portsStr.substring(0, dashIdx).trim());
            int end = Integer.parseInt(portsStr.substring(dashIdx + 1).trim());
            if (start < 1 || end > 65535 || start > end) {
                return null;
            }
            return new int[]{start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String asString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return ((String) obj).trim();
        return obj.toString().trim();
    }

    private static int asInt(Object obj, int defaultVal) {
        if (obj instanceof Integer) return ((Integer) obj).intValue();
        if (obj instanceof Long) return (int) ((Long) obj).longValue();
        if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }
}
