package com.reajason.noone.plugin;

import java.io.*;
import java.util.*;

/**
 * Log monitor plugin implementing tail-f like behavior.
 * Designed for async execution via TaskManager - runs indefinitely
 * until cancelled, continuously updating ctx result with new log lines.
 *
 * @author ReaJason
 * @since 2026/3/6
 */
public class LogMonitor {

    private static final int DEFAULT_INITIAL_LINES = 50;
    private static final int DEFAULT_POLL_INTERVAL_MS = 500;
    private static final int MAX_BUFFER_LINES = 1000;

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Map)) {
            return true;
        }
        Map<String, Object> ctx = (Map<String, Object>) obj;
        Map<String, Object> result = new HashMap<String, Object>();
        ctx.put("result", result);

        String path = asString(ctx.get("path"));
        if (path == null || path.isEmpty()) {
            result.put("error", "path is required");
            return true;
        }

        int initialLines = asInt(ctx.get("initialLines"), DEFAULT_INITIAL_LINES);
        int pollInterval = asInt(ctx.get("pollInterval"), DEFAULT_POLL_INTERVAL_MS);

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            result.put("error", "file not found: " + path);
            return true;
        }
        if (!file.canRead()) {
            result.put("error", "file not readable: " + path);
            return true;
        }

        result.put("path", path);
        result.put("status", "monitoring");
        result.put("linesRead", Integer.valueOf(0));

        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            long fileLength = raf.length();

            long startPos = findTailPosition(raf, fileLength, initialLines);
            raf.seek(startPos);

            List<String> buffer = new ArrayList<String>();
            int totalLinesRead = 0;

            String initialContent = readLines(raf, buffer, initialLines);
            totalLinesRead += buffer.size();
            result.put("lines", initialContent);
            result.put("linesRead", Integer.valueOf(totalLinesRead));

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(pollInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                long currentLength = file.length();
                long currentPos = raf.getFilePointer();

                if (currentLength < currentPos) {
                    raf.seek(0);
                    buffer.clear();
                    totalLinesRead = 0;
                    result.put("lines", "--- file truncated, reading from start ---\n");
                }

                if (currentLength > currentPos) {
                    buffer.clear();
                    String newContent = readLines(raf, buffer, MAX_BUFFER_LINES);
                    if (buffer.size() > 0) {
                        totalLinesRead += buffer.size();
                        String existing = (String) result.get("lines");
                        if (existing == null) {
                            existing = "";
                        }
                        String combined = existing + newContent;
                        if (combined.length() > 512 * 1024) {
                            combined = combined.substring(combined.length() - 256 * 1024);
                        }
                        result.put("lines", combined);
                        result.put("linesRead", Integer.valueOf(totalLinesRead));
                    }
                }
            }

            result.put("status", "stopped");
        } catch (IOException e) {
            result.put("status", "error");
            result.put("error", "IO error: " + e.getMessage());
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
            }
        }

        return true;
    }

    private long findTailPosition(RandomAccessFile raf, long fileLength, int lines) throws IOException {
        if (fileLength == 0 || lines <= 0) {
            return fileLength;
        }

        long pos = fileLength - 1;
        int lineCount = 0;

        while (pos > 0 && lineCount <= lines) {
            raf.seek(pos);
            if (raf.readByte() == '\n') {
                lineCount++;
            }
            pos--;
        }

        if (pos == 0) {
            return 0;
        }
        return pos + 2;
    }

    private String readLines(RandomAccessFile raf, List<String> buffer, int maxLines) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = raf.readLine()) != null && buffer.size() < maxLines) {
            buffer.add(line);
            sb.append(line).append('\n');
        }
        return sb.toString();
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
