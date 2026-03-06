package com.reajason.noone.plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pluggable async/scheduled task execution manager.
 * SINGLE CLASS ONLY — no inner classes, no anonymous classes.
 * Uses Runnable interface implemented by this class itself, with
 * thread-name-based dispatch to differentiate worker vs scheduled threads.
 *
 * @author ReaJason
 * @since 2026/3/6
 */
public class TaskManager implements Runnable {

    private static final String OP = "op";
    private static final String TASK_ID = "taskId";
    private static final String TARGET_PLUGIN = "targetPlugin";
    private static final String TARGET_ARGS = "targetArgs";
    private static final String DELAY = "delay";
    private static final String PERIOD = "period";

    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_SCHEDULED = "SCHEDULED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String THREAD_PREFIX_WORKER = "noone-task-";
    private static final String THREAD_PREFIX_SCHED = "noone-sched-";
    private static final int MAX_WORKERS = 8;
    private static final int MAX_COMPLETED_TASKS = 200;

    private final AtomicLong taskIdGen = new AtomicLong(0);
    private final AtomicLong workerCounter = new AtomicLong(0);

    private final Map<String, Map<String, Object>> taskStore =
            new ConcurrentHashMap<String, Map<String, Object>>();
    private final Map<String, Thread> threadStore =
            new ConcurrentHashMap<String, Thread>();

    private final LinkedBlockingQueue<String> asyncQueue =
            new LinkedBlockingQueue<String>(256);
    private final List<Thread> workerThreads =
            Collections.synchronizedList(new ArrayList<Thread>());

    private volatile boolean shutdownFlag = false;
    private Map<String, Object> pluginCaches;
    private Map<String, Object> globalCachesRef;

    /**
     * Thread entry point. Dispatches based on thread name prefix:
     * - "noone-task-*" → async worker loop (polls from asyncQueue)
     * - "noone-sched-*" → scheduled task (taskId encoded in name)
     */
    public void run() {
        String name = Thread.currentThread().getName();
        if (name.startsWith(THREAD_PREFIX_SCHED)) {
            String taskId = name.substring(THREAD_PREFIX_SCHED.length());
            runScheduledTask(taskId);
        } else {
            runWorkerLoop();
        }
    }

    private void runWorkerLoop() {
        while (!shutdownFlag && !Thread.currentThread().isInterrupted()) {
            try {
                String taskId = asyncQueue.poll(2, TimeUnit.SECONDS);
                if (taskId != null) {
                    executeAsyncTask(taskId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void executeAsyncTask(String taskId) {
        Map<String, Object> taskInfo = taskStore.get(taskId);
        if (taskInfo == null) {
            return;
        }
        threadStore.put(taskId, Thread.currentThread());
        taskInfo.put("status", STATUS_RUNNING);
        taskInfo.put("startTime", Long.valueOf(System.currentTimeMillis()));

        Map<String, Object> taskCtx = (Map<String, Object>) taskInfo.get("ctx");
        Object plugin = pluginCaches != null ? pluginCaches.get(taskInfo.get("plugin")) : null;
        if (taskCtx == null || plugin == null) {
            taskInfo.put("status", STATUS_FAILED);
            taskInfo.put("error", "missing ctx or plugin");
            taskInfo.put("endTime", Long.valueOf(System.currentTimeMillis()));
            return;
        }

        try {
            plugin.equals(taskCtx);
            taskInfo.put("status", STATUS_COMPLETED);
            taskInfo.put("result", taskCtx.get("result"));
        } catch (Throwable t) {
            taskInfo.put("status", STATUS_FAILED);
            taskInfo.put("error", t.toString());
        } finally {
            taskInfo.put("endTime", Long.valueOf(System.currentTimeMillis()));
            taskInfo.remove("ctx");
            threadStore.remove(taskId);
        }
    }

    @SuppressWarnings("unchecked")
    private void runScheduledTask(String taskId) {
        Map<String, Object> taskInfo = taskStore.get(taskId);
        if (taskInfo == null) {
            return;
        }

        long delayMs = asLong(taskInfo.get("_delay"), 0L);
        long periodMs = asLong(taskInfo.get("period"), 0L);

        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                taskInfo.put("status", STATUS_CANCELLED);
                taskInfo.put("endTime", Long.valueOf(System.currentTimeMillis()));
                taskInfo.remove("ctx");
                threadStore.remove(taskId);
                return;
            }
        }

        boolean periodic = periodMs > 0;

        while (!shutdownFlag && !Thread.currentThread().isInterrupted()) {
            taskInfo.put("status", STATUS_RUNNING);
            taskInfo.put("lastRunTime", Long.valueOf(System.currentTimeMillis()));

            Map<String, Object> taskCtx = (Map<String, Object>) taskInfo.get("ctx");
            Object plugin = pluginCaches != null ? pluginCaches.get(taskInfo.get("plugin")) : null;
            if (taskCtx == null || plugin == null) {
                taskInfo.put("lastRunStatus", STATUS_FAILED);
                taskInfo.put("lastRunError", "missing ctx or plugin");
                break;
            }

            try {
                taskCtx.remove("result");
                plugin.equals(taskCtx);
                taskInfo.put("lastResult", taskCtx.get("result"));
                taskInfo.put("lastRunStatus", STATUS_COMPLETED);
            } catch (Throwable t) {
                taskInfo.put("lastRunStatus", STATUS_FAILED);
                taskInfo.put("lastRunError", t.toString());
            }

            if (!periodic) {
                taskInfo.put("status", STATUS_COMPLETED);
                taskInfo.put("endTime", Long.valueOf(System.currentTimeMillis()));
                taskInfo.remove("ctx");
                threadStore.remove(taskId);
                return;
            }

            taskInfo.put("status", STATUS_SCHEDULED);
            try {
                Thread.sleep(periodMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        taskInfo.put("status", STATUS_CANCELLED);
        taskInfo.put("endTime", Long.valueOf(System.currentTimeMillis()));
        taskInfo.remove("ctx");
        threadStore.remove(taskId);
    }

    private void ensureWorkers() {
        int alive = 0;
        synchronized (workerThreads) {
            Iterator<Thread> it = workerThreads.iterator();
            while (it.hasNext()) {
                if (!it.next().isAlive()) {
                    it.remove();
                } else {
                    alive++;
                }
            }
        }
        while (alive < 2 || (asyncQueue.size() > alive && alive < MAX_WORKERS)) {
            Thread t = new Thread(this, THREAD_PREFIX_WORKER + workerCounter.incrementAndGet());
            t.setDaemon(true);
            t.start();
            workerThreads.add(t);
            alive++;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Map)) {
            return true;
        }
        Map<String, Object> ctx = (Map<String, Object>) obj;

        if (pluginCaches == null) {
            pluginCaches = (Map<String, Object>) ctx.get("pluginCaches");
        }
        if (globalCachesRef == null) {
            globalCachesRef = (Map<String, Object>) ctx.get("globalCaches");
            if (globalCachesRef != null) {
                globalCachesRef.put("taskManager", this);
            }
        }

        Map<String, Object> result = new HashMap<String, Object>();
        String op = (String) ctx.get(OP);

        if ("submit".equals(op)) {
            handleSubmit(ctx, result);
        } else if ("schedule".equals(op)) {
            handleSchedule(ctx, result);
        } else if ("status".equals(op)) {
            handleStatus(ctx, result);
        } else if ("cancel".equals(op) || "stop".equals(op)) {
            handleCancel(ctx, result);
        } else if ("list".equals(op)) {
            handleList(result);
        } else if ("clean".equals(op)) {
            handleClean(ctx, result);
        } else if ("shutdown".equals(op)) {
            handleShutdown(result);
        } else if ("info".equals(op)) {
            handleInfo(result);
        } else {
            result.put("error", "unknown op: " + op);
        }

        ctx.put("result", result);
        return true;
    }

    @SuppressWarnings("unchecked")
    private void handleSubmit(Map<String, Object> ctx, Map<String, Object> result) {
        String targetPluginName = (String) ctx.get(TARGET_PLUGIN);
        if (targetPluginName == null || pluginCaches == null) {
            result.put("error", "targetPlugin is required and pluginCaches must be available");
            return;
        }
        Object targetPlugin = pluginCaches.get(targetPluginName);
        if (targetPlugin == null) {
            result.put("error", "plugin not loaded: " + targetPluginName);
            return;
        }

        Map<String, Object> targetArgs = (Map<String, Object>) ctx.get(TARGET_ARGS);
        if (targetArgs == null) {
            targetArgs = new HashMap<String, Object>();
        }

        cleanExpiredTasks();

        String taskId = String.valueOf(taskIdGen.incrementAndGet());
        Map<String, Object> taskCtx = new HashMap<String, Object>(targetArgs);
        taskCtx.put("pluginCaches", pluginCaches);
        if (globalCachesRef != null) {
            taskCtx.put("globalCaches", globalCachesRef);
        }

        Map<String, Object> taskInfo = new ConcurrentHashMap<String, Object>();
        taskInfo.put("taskId", taskId);
        taskInfo.put("status", STATUS_SUBMITTED);
        taskInfo.put("plugin", targetPluginName);
        taskInfo.put("submitTime", Long.valueOf(System.currentTimeMillis()));
        taskInfo.put("ctx", taskCtx);
        taskStore.put(taskId, taskInfo);

        if (!asyncQueue.offer(taskId)) {
            taskInfo.put("status", STATUS_FAILED);
            taskInfo.put("error", "task rejected: queue full");
            taskInfo.remove("ctx");
        } else {
            ensureWorkers();
        }

        result.put("taskId", taskId);
        result.put("status", taskInfo.get("status"));
    }

    @SuppressWarnings("unchecked")
    private void handleSchedule(Map<String, Object> ctx, Map<String, Object> result) {
        String targetPluginName = (String) ctx.get(TARGET_PLUGIN);
        if (targetPluginName == null || pluginCaches == null) {
            result.put("error", "targetPlugin is required and pluginCaches must be available");
            return;
        }
        Object targetPlugin = pluginCaches.get(targetPluginName);
        if (targetPlugin == null) {
            result.put("error", "plugin not loaded: " + targetPluginName);
            return;
        }

        Map<String, Object> targetArgs = (Map<String, Object>) ctx.get(TARGET_ARGS);
        if (targetArgs == null) {
            targetArgs = new HashMap<String, Object>();
        }

        long delayMs = asLong(ctx.get(DELAY), 0L);
        long periodMs = asLong(ctx.get(PERIOD), 0L);

        cleanExpiredTasks();

        String taskId = String.valueOf(taskIdGen.incrementAndGet());
        Map<String, Object> taskCtx = new HashMap<String, Object>(targetArgs);
        taskCtx.put("pluginCaches", pluginCaches);
        if (globalCachesRef != null) {
            taskCtx.put("globalCaches", globalCachesRef);
        }

        Map<String, Object> taskInfo = new ConcurrentHashMap<String, Object>();
        taskInfo.put("taskId", taskId);
        taskInfo.put("status", STATUS_SCHEDULED);
        taskInfo.put("plugin", targetPluginName);
        taskInfo.put("submitTime", Long.valueOf(System.currentTimeMillis()));
        taskInfo.put("ctx", taskCtx);
        taskInfo.put("_delay", Long.valueOf(delayMs));
        if (periodMs > 0) {
            taskInfo.put("period", Long.valueOf(periodMs));
        }
        taskStore.put(taskId, taskInfo);

        Thread schedThread = new Thread(this, THREAD_PREFIX_SCHED + taskId);
        schedThread.setDaemon(true);
        threadStore.put(taskId, schedThread);
        schedThread.start();

        result.put("taskId", taskId);
        result.put("status", taskInfo.get("status"));
    }

    @SuppressWarnings("unchecked")
    private void handleStatus(Map<String, Object> ctx, Map<String, Object> result) {
        String taskId = (String) ctx.get(TASK_ID);
        if (taskId == null) {
            result.put("error", "taskId is required");
            return;
        }
        Map<String, Object> taskInfo = taskStore.get(taskId);
        if (taskInfo == null) {
            result.put("error", "task not found: " + taskId);
            return;
        }
        result.put("taskId", taskId);
        result.put("status", taskInfo.get("status"));
        result.put("plugin", taskInfo.get("plugin"));
        result.put("submitTime", taskInfo.get("submitTime"));
        result.put("startTime", taskInfo.get("startTime"));
        result.put("endTime", taskInfo.get("endTime"));

        String status = (String) taskInfo.get("status");
        if (STATUS_COMPLETED.equals(status)) {
            result.put("result", taskInfo.get("result"));
        } else if (STATUS_FAILED.equals(status)) {
            result.put("error", taskInfo.get("error"));
        } else if (STATUS_RUNNING.equals(status) || STATUS_SUBMITTED.equals(status)) {
            Map<String, Object> taskCtx = (Map<String, Object>) taskInfo.get("ctx");
            if (taskCtx != null) {
                Object partialResult = taskCtx.get("result");
                if (partialResult != null) {
                    result.put("partialResult", partialResult);
                }
            }
        }

        if (STATUS_SCHEDULED.equals(status) || STATUS_RUNNING.equals(status)) {
            result.put("lastResult", taskInfo.get("lastResult"));
            result.put("lastRunStatus", taskInfo.get("lastRunStatus"));
            result.put("lastRunError", taskInfo.get("lastRunError"));
            result.put("lastRunTime", taskInfo.get("lastRunTime"));
            result.put("period", taskInfo.get("period"));
        }
    }

    private void handleCancel(Map<String, Object> ctx, Map<String, Object> result) {
        String taskId = (String) ctx.get(TASK_ID);
        if (taskId == null) {
            result.put("error", "taskId is required");
            return;
        }
        Map<String, Object> taskInfo = taskStore.get(taskId);
        if (taskInfo == null) {
            result.put("error", "task not found: " + taskId);
            return;
        }

        boolean removed = asyncQueue.remove(taskId);
        Thread thread = threadStore.remove(taskId);
        boolean interrupted = false;
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            interrupted = true;
        }

        String status = (String) taskInfo.get("status");
        if (!STATUS_COMPLETED.equals(status) && !STATUS_FAILED.equals(status)) {
            taskInfo.put("status", STATUS_CANCELLED);
            taskInfo.put("endTime", Long.valueOf(System.currentTimeMillis()));
            taskInfo.remove("ctx");
        }

        result.put("taskId", taskId);
        result.put("cancelled", Boolean.valueOf(interrupted));
        result.put("status", taskInfo.get("status"));
    }

    private void handleList(Map<String, Object> result) {
        List<Map<String, Object>> tasks = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Map<String, Object>> entry : taskStore.entrySet()) {
            Map<String, Object> taskInfo = entry.getValue();
            Map<String, Object> summary = new HashMap<String, Object>();
            summary.put("taskId", taskInfo.get("taskId"));
            summary.put("status", taskInfo.get("status"));
            summary.put("plugin", taskInfo.get("plugin"));
            summary.put("submitTime", taskInfo.get("submitTime"));
            summary.put("endTime", taskInfo.get("endTime"));
            tasks.add(summary);
        }
        result.put("tasks", tasks);
        result.put("count", Integer.valueOf(tasks.size()));
    }

    private void handleClean(Map<String, Object> ctx, Map<String, Object> result) {
        String taskId = (String) ctx.get(TASK_ID);
        if (taskId != null) {
            Map<String, Object> removed = taskStore.remove(taskId);
            threadStore.remove(taskId);
            result.put("cleaned", Boolean.valueOf(removed != null));
        } else {
            int count = 0;
            Iterator<Map.Entry<String, Map<String, Object>>> it = taskStore.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Map<String, Object>> entry = it.next();
                String status = (String) entry.getValue().get("status");
                if (STATUS_COMPLETED.equals(status) || STATUS_FAILED.equals(status) || STATUS_CANCELLED.equals(status)) {
                    threadStore.remove(entry.getKey());
                    it.remove();
                    count++;
                }
            }
            result.put("cleaned", Integer.valueOf(count));
        }
    }

    private void handleShutdown(Map<String, Object> result) {
        shutdownFlag = true;
        for (Thread t : workerThreads) {
            if (t.isAlive()) {
                t.interrupt();
            }
        }
        for (Thread t : threadStore.values()) {
            if (t.isAlive()) {
                t.interrupt();
            }
        }
        for (Map<String, Object> taskInfo : taskStore.values()) {
            taskInfo.remove("ctx");
        }
        workerThreads.clear();
        threadStore.clear();
        taskStore.clear();
        asyncQueue.clear();
        result.put("shutdown", Boolean.TRUE);
    }

    private void handleInfo(Map<String, Object> result) {
        int total = taskStore.size();
        int running = 0;
        int scheduled = 0;
        int completed = 0;
        for (Map<String, Object> taskInfo : taskStore.values()) {
            String s = (String) taskInfo.get("status");
            if (STATUS_RUNNING.equals(s) || STATUS_SUBMITTED.equals(s)) {
                running++;
            } else if (STATUS_SCHEDULED.equals(s)) {
                scheduled++;
            } else {
                completed++;
            }
        }
        result.put("totalTasks", Integer.valueOf(total));
        result.put("runningTasks", Integer.valueOf(running));
        result.put("scheduledTasks", Integer.valueOf(scheduled));
        result.put("completedTasks", Integer.valueOf(completed));
        result.put("workerThreads", Integer.valueOf(workerThreads.size()));
        result.put("scheduledThreads", Integer.valueOf(threadStore.size()));
    }

    private void cleanExpiredTasks() {
        int completedCount = 0;
        for (Map<String, Object> taskInfo : taskStore.values()) {
            String s = (String) taskInfo.get("status");
            if (STATUS_COMPLETED.equals(s) || STATUS_FAILED.equals(s) || STATUS_CANCELLED.equals(s)) {
                completedCount++;
            }
        }
        if (completedCount <= MAX_COMPLETED_TASKS) {
            return;
        }

        String oldestId = null;
        long oldestTime = Long.MAX_VALUE;
        int removed = 0;
        int toRemove = completedCount - MAX_COMPLETED_TASKS / 2;

        while (removed < toRemove) {
            oldestId = null;
            oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, Map<String, Object>> entry : taskStore.entrySet()) {
                String s = (String) entry.getValue().get("status");
                if (STATUS_COMPLETED.equals(s) || STATUS_FAILED.equals(s) || STATUS_CANCELLED.equals(s)) {
                    long t = asLong(entry.getValue().get("endTime"), 0L);
                    if (t < oldestTime) {
                        oldestTime = t;
                        oldestId = entry.getKey();
                    }
                }
            }
            if (oldestId == null) {
                break;
            }
            taskStore.remove(oldestId);
            threadStore.remove(oldestId);
            removed++;
        }
    }

    private static long asLong(Object obj, long defaultVal) {
        if (obj instanceof Long) {
            return ((Long) obj).longValue();
        }
        if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        }
        if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }
}
