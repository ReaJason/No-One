package com.reajason.noone.server.shell.oplog;

import java.util.Map;

public record ShellOperationLogEvent(
        Long shellId,
        String username,
        ShellOperationType operation,
        String pluginId,
        String action,
        Map<String, Object> args,
        Map<String, Object> result,
        boolean success,
        String errorMessage,
        long durationMs
) {
}
