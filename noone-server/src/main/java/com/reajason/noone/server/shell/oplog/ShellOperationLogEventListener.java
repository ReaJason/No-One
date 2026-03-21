package com.reajason.noone.server.shell.oplog;

import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ShellOperationLogEventListener {

    @Resource
    private ShellOperationLogService shellOperationLogService;

    @Async("shellOpLogExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true)
    public void handle(ShellOperationLogEvent event) {
        shellOperationLogService.record(event);
    }
}
