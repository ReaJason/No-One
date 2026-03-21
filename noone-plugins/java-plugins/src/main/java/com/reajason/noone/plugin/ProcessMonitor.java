package com.reajason.noone.plugin;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Process monitor plugin that reads the Linux /proc filesystem
 * to enumerate running processes without executing any commands.
 */
public class ProcessMonitor {

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Map)) {
            return true;
        }
        Map<String, Object> ctx = (Map<String, Object>) obj;
        HashMap<String, Object> result = new HashMap<String, Object>();
        try {
            String osName = System.getProperty("os.name", "");
            if (!osName.toLowerCase().contains("linux")) {
                result.put("error", "Process monitoring via /proc is only supported on Linux");
                ctx.put("result", result);
                return true;
            }

            File procDir = new File("/proc");
            if (!procDir.exists() || !procDir.isDirectory()) {
                result.put("error", "/proc filesystem not available");
                ctx.put("result", result);
                return true;
            }

            HashMap<String, String> uidMap = buildUidMap();

            String[] entries = procDir.list();
            List<Map<String, Object>> processes = new ArrayList<Map<String, Object>>();
            if (entries != null) {
                for (int i = 0; i < entries.length; i++) {
                    if (isNumeric(entries[i])) {
                        HashMap<String, Object> proc = readProcessInfo(entries[i], uidMap);
                        if (proc != null) {
                            processes.add(proc);
                        }
                    }
                }
            }

            result.put("os", "Linux");
            result.put("total", Integer.valueOf(processes.size()));
            result.put("processes", processes);
        } catch (Exception e) {
            result.put("error", "Failed to monitor processes: " + e.getMessage());
        }
        ctx.put("result", result);
        return true;
    }

    private static HashMap<String, Object> readProcessInfo(String pid, HashMap<String, String> uidMap) {
        File statusFile = new File("/proc/" + pid + "/status");
        if (!statusFile.exists()) {
            return null;
        }

        HashMap<String, Object> proc = new HashMap<String, Object>();
        proc.put("pid", pid);

        String statusContent = readFileContent(statusFile);
        if (statusContent == null) {
            return null;
        }
        parseStatus(statusContent, proc);

        String uid = (String) proc.get("uid");
        if (uid != null && uidMap.containsKey(uid)) {
            proc.put("user", uidMap.get(uid));
        } else {
            proc.put("user", uid != null ? uid : "unknown");
        }

        File cmdlineFile = new File("/proc/" + pid + "/cmdline");
        String cmdline = readFileContent(cmdlineFile);
        if (cmdline != null && cmdline.length() > 0) {
            proc.put("command", cmdline.replace('\0', ' ').trim());
        } else {
            Object name = proc.get("name");
            proc.put("command", name != null ? "[" + name + "]" : "");
        }

        return proc;
    }

    private static void parseStatus(String content, HashMap<String, Object> proc) {
        int start = 0;
        int len = content.length();
        while (start < len) {
            int lineEnd = content.indexOf('\n', start);
            if (lineEnd == -1) {
                lineEnd = len;
            }
            String line = content.substring(start, lineEnd);
            start = lineEnd + 1;

            int colonIdx = line.indexOf(':');
            if (colonIdx == -1) {
                continue;
            }
            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();

            if ("Name".equals(key)) {
                proc.put("name", value);
            } else if ("State".equals(key)) {
                int spaceIdx = value.indexOf(' ');
                proc.put("state", spaceIdx > 0 ? value.substring(0, spaceIdx) : value);
            } else if ("PPid".equals(key)) {
                proc.put("ppid", value);
            } else if ("Uid".equals(key)) {
                int tabIdx = value.indexOf('\t');
                proc.put("uid", tabIdx > 0 ? value.substring(0, tabIdx) : value);
            } else if ("VmRSS".equals(key)) {
                proc.put("vmRss", parseKbValue(value));
            } else if ("VmSize".equals(key)) {
                proc.put("vmSize", parseKbValue(value));
            } else if ("Threads".equals(key)) {
                proc.put("threads", value);
            }
        }
    }

    private static String parseKbValue(String value) {
        int spaceIdx = value.indexOf(' ');
        String numStr = spaceIdx > 0 ? value.substring(0, spaceIdx) : value;
        try {
            long kb = Long.parseLong(numStr.trim());
            return String.valueOf(kb * 1024);
        } catch (NumberFormatException e) {
            return numStr;
        }
    }

    private static HashMap<String, String> buildUidMap() {
        HashMap<String, String> map = new HashMap<String, String>();
        File passwdFile = new File("/etc/passwd");
        String content = readFileContent(passwdFile);
        if (content == null) {
            return map;
        }

        int start = 0;
        int len = content.length();
        while (start < len) {
            int lineEnd = content.indexOf('\n', start);
            if (lineEnd == -1) {
                lineEnd = len;
            }
            String line = content.substring(start, lineEnd);
            start = lineEnd + 1;

            if (line.length() == 0 || line.charAt(0) == '#') {
                continue;
            }
            // format: username:x:uid:gid:...
            int first = line.indexOf(':');
            if (first == -1) {
                continue;
            }
            int second = line.indexOf(':', first + 1);
            if (second == -1) {
                continue;
            }
            int third = line.indexOf(':', second + 1);
            if (third == -1) {
                continue;
            }
            String username = line.substring(0, first);
            String uid = line.substring(second + 1, third);
            map.put(uid, username);
        }
        return map;
    }

    private static String readFileContent(File file) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString("UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean isNumeric(String str) {
        if (str == null || str.length() == 0) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
