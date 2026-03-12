package com.reajason.noone.server.shell;

import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.server.admin.plugin.PluginRepository;
import com.reajason.noone.server.shell.oplog.ShellOperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShellServiceTestConnectionTest {

    private ShellService shellService;
    private ShellRepository shellRepository;
    private ShellConnectionPool shellConnectionPool;

    @BeforeEach
    void setUp() {
        shellService = new ShellService();
        shellRepository = mock(ShellRepository.class);
        shellConnectionPool = mock(ShellConnectionPool.class);

        ReflectionTestUtils.setField(shellService, "shellRepository", shellRepository);
        ReflectionTestUtils.setField(shellService, "shellConnectionPool", shellConnectionPool);
        ReflectionTestUtils.setField(shellService, "pluginRepository", mock(PluginRepository.class));
        ReflectionTestUtils.setField(shellService, "shellStatusUpdater", mock(ShellStatusUpdater.class));
        ReflectionTestUtils.setField(shellService, "shellMapper", mock(ShellMapper.class));
        ReflectionTestUtils.setField(shellService, "shellOperationLogService", mock(ShellOperationLogService.class));
    }

    @Test
    void shouldPopulateLastOnlineAtWhenConnectionTestSucceeds() {
        Long shellId = 1L;
        Shell shell = shell(shellId);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.test()).thenReturn(true);

        boolean connected = shellService.testConnection(shellId);

        assertTrue(connected);
        assertEquals(ShellStatus.CONNECTED, shell.getStatus());
        assertNotNull(shell.getLastOnlineAt());
    }

    @Test
    void shouldKeepExistingLastOnlineAtWhenConnectionTestFails() {
        Long shellId = 2L;
        LocalDateTime previousLastOnlineAt = LocalDateTime.of(2026, 3, 10, 8, 0, 0);
        Shell shell = shell(shellId);
        shell.setLastOnlineAt(previousLastOnlineAt);
        ShellConnection connection = mock(ShellConnection.class);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));
        when(shellConnectionPool.getOrCreateCached(shell)).thenReturn(connection);
        when(connection.test()).thenReturn(false);

        boolean connected = shellService.testConnection(shellId);

        assertFalse(connected);
        assertEquals(ShellStatus.ERROR, shell.getStatus());
        assertEquals(previousLastOnlineAt, shell.getLastOnlineAt());
    }

    private Shell shell(Long id) {
        Shell shell = new Shell();
        shell.setId(id);
        shell.setLanguage(ShellLanguage.JAVA);
        shell.setUrl("http://127.0.0.1/test");
        return shell;
    }
}
