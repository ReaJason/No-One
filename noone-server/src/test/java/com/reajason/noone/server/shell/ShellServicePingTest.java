package com.reajason.noone.server.shell;

import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.client.RequestSendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShellServicePingTest {

    private ShellService shellService;
    private ShellRepository shellRepository;
    private ShellConnectionPool shellConnectionPool;
    private ShellLookupHelper shellLookupHelper;
    private ShellCoreInitHelper shellCoreInitHelper;

    @BeforeEach
    void setUp() {
        shellService = new ShellService();
        shellRepository = mock(ShellRepository.class);
        shellConnectionPool = mock(ShellConnectionPool.class);
        shellLookupHelper = mock(ShellLookupHelper.class);
        shellCoreInitHelper = mock(ShellCoreInitHelper.class);

        ReflectionTestUtils.setField(shellService, "shellRepository", shellRepository);
        ReflectionTestUtils.setField(shellService, "shellConnectionPool", shellConnectionPool);
        ReflectionTestUtils.setField(shellService, "shellResponseHelper", new ShellResponseHelper());
        ReflectionTestUtils.setField(shellService, "shellLookupHelper", shellLookupHelper);
        ReflectionTestUtils.setField(shellService, "shellCoreInitHelper", shellCoreInitHelper);

        when(shellRepository.save(any(Shell.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldSkipInitCoreForNonStagingShell() {
        Long shellId = 1L;
        Shell shell = shell(shellId);
        shell.setStaging(false);
        when(shellLookupHelper.requireById(shellId)).thenReturn(shell);

        Map<String, Object> result = shellService.initCore(shellId);

        assertEquals(true, result.get("success"));
        assertEquals(false, result.get("staging"));
        assertEquals(true, result.get("skipped"));
        assertEquals(0L, result.get("durationMs"));
        verify(shellConnectionPool, never()).getOrCreateCached(any());
    }

    @Test
    void shouldReturnConnectedWithoutRecoveryWhenFirstStatusProbeSucceeds() {
        Long shellId = 2L;
        Shell shell = shell(shellId);
        shell.setStaging(true);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellLookupHelper.requireById(shellId)).thenReturn(shell);
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.checkStatus()).thenReturn(true);

        Map<String, Object> result = shellService.ping(shellId);

        assertEquals(true, result.get("connected"));
        assertEquals(false, result.get("recoveryAttempted"));
        assertEquals(false, result.get("recovered"));
        assertEquals("CONNECTED", result.get("status"));
        assertTrue(shell.getLastOnlineAt() != null);
        verify(shellCoreInitHelper, never()).initCoreIfNeeded(any(), anyLong());
        verify(connection, times(1)).checkStatus();
    }

    @Test
    void shouldReinjectCoreAndRetryStatusWhenFirstProbeThrowsCommunicationException() {
        Long shellId = 3L;
        Shell shell = shell(shellId);
        shell.setStaging(true);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellLookupHelper.requireById(shellId)).thenReturn(shell);
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.checkStatus())
                .thenThrow(new RequestSendException("status probe failed", 1, new RuntimeException("io")))
                .thenReturn(true);

        Map<String, Object> result = shellService.ping(shellId);

        assertEquals(true, result.get("connected"));
        assertEquals(true, result.get("recoveryAttempted"));
        assertEquals(true, result.get("recovered"));
        assertEquals("CONNECTED", result.get("status"));
        assertNotNull(shell.getLastOnlineAt());
        verify(shellCoreInitHelper).initCoreIfNeeded(connection, shellId);
        verify(connection, times(2)).checkStatus();
    }

    @Test
    void shouldNotAttemptRecoveryForNonStagingShellWhenStatusProbeFails() {
        Long shellId = 4L;
        Shell shell = shell(shellId);
        shell.setStaging(false);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellLookupHelper.requireById(shellId)).thenReturn(shell);
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.checkStatus())
                .thenThrow(new RequestSendException("status probe failed", 1, new RuntimeException("io")));

        Map<String, Object> result = shellService.ping(shellId);

        assertEquals(false, result.get("connected"));
        assertEquals(false, result.get("recoveryAttempted"));
        assertEquals(false, result.get("recovered"));
        assertEquals("ERROR", result.get("status"));
        assertEquals(ShellStatus.ERROR, shell.getStatus());
        verify(shellCoreInitHelper, never()).initCoreIfNeeded(any(), anyLong());
        verify(connection, times(1)).checkStatus();
    }

    private Shell shell(Long id) {
        Shell shell = new Shell();
        shell.setId(id);
        shell.setLanguage(ShellLanguage.JAVA);
        shell.setUrl("http://127.0.0.1/test");
        shell.setStatus(ShellStatus.DISCONNECTED);
        return shell;
    }
}
