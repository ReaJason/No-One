package com.reajason.noone.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThreadDumpCollector {

    @Override
    public boolean equals(Object obj) {
        Map<String, Object> map = (Map<String, Object>) obj;
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
            Map<String, Object> errorInfo = new HashMap<String, Object>();
            errorInfo.put("error", "Failed to get threads info: " + e.getMessage());
            threadsInfo.add(errorInfo);
        }
        map.put("result", threadsInfo);
        return true;
    }
}
