package com.reajason.noone.server.shell.oplog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShellOperationLogEventListenerTest {

    @InjectMocks
    private ShellOperationLogEventListener listener;

    @Mock
    private ShellOperationLogService shellOperationLogService;

    @Test
    void shouldDelegateToService() {
        ShellOperationLogEvent event = new ShellOperationLogEvent(
                1L, "user1", ShellOperationType.DISPATCH,
                "system-info", null, Map.of(), Map.of("code", 1),
                true, null, 100L
        );

        listener.handle(event);

        verify(shellOperationLogService).record(event);
    }
}
