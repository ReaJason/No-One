package com.reajason.noone.server.shell.oplog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShellOperationLogServiceRecordEventTest {

    @InjectMocks
    private ShellOperationLogService service;

    @Mock
    private ShellOperationLogRepository repository;

    @Mock
    private ShellOperationLogMapper mapper;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void shouldPersistOperationLogFromEvent() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"code\":1}");

        ShellOperationLogEvent event = new ShellOperationLogEvent(
                1L, "testuser", ShellOperationType.DISPATCH,
                "file-manager", "list", Map.of("path", "/"),
                Map.of("code", 1), true, null, 42L
        );

        service.record(event);

        ArgumentCaptor<ShellOperationLog> captor = ArgumentCaptor.forClass(ShellOperationLog.class);
        verify(repository).save(captor.capture());

        ShellOperationLog saved = captor.getValue();
        assertEquals(1L, saved.getShellId());
        assertEquals("testuser", saved.getUsername());
        assertEquals(ShellOperationType.DISPATCH, saved.getOperation());
        assertEquals("file-manager", saved.getPluginId());
        assertEquals("list", saved.getAction());
        assertTrue(saved.isSuccess());
        assertEquals(42L, saved.getDurationMs());
    }
}
