package com.reajason.noone.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThreadDumpCollector {

    public Object execute(Map<String, Object> map) {
        List<Map<String, Object>> threadsInfo = new ArrayList<Map<String, Object>>();
        try {
            Thread[] threads = new Thread[Thread.activeCount()];
            Thread.enumerate(threads);

            for (Thread thread : threads) {
                if (thread != null) {
                    Map<String, Object> threadInfo = new HashMap<String, Object>();
                    threadInfo.put("id", thread.getId());
                    threadInfo.put("name", thread.getName());
                    threadInfo.put("priority", thread.getPriority());
                    threadInfo.put("state", thread.getState().toString());
                    threadInfo.put("daemon", thread.isDaemon());
                    threadInfo.put("alive", thread.isAlive());
                    threadsInfo.add(threadInfo);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get threads info: " + e.getMessage(), e);
        }
        return threadsInfo;
    }
}
