package com.reajason.noone.server.shell;

import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.server.shell.oplog.ShellOperationLogEvent;
import com.reajason.noone.server.shell.oplog.ShellOperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShellCoreInitHelper {

    private final ApplicationEventPublisher eventPublisher;

    public boolean initCoreIfNeeded(ShellConnection connection, Long shellId) {
        if (connection.getLoaderClient() == null) {
            return true;
        }
        long start = System.currentTimeMillis();
        String username = getCurrentUsername();
        try {
            boolean result = connection.init();
            long durationMs = System.currentTimeMillis() - start;
            eventPublisher.publishEvent(new ShellOperationLogEvent(
                    shellId, username, ShellOperationType.LOAD_CORE,
                    null, "init-core", null, Map.of("success", result),
                    result, result ? null : "Core injection returned false", durationMs));
            return result;
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            eventPublisher.publishEvent(new ShellOperationLogEvent(
                    shellId, username, ShellOperationType.LOAD_CORE,
                    null, "init-core", null, null,
                    false, e.getMessage(), durationMs));
            throw e;
        }
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
