package com.reajason.noone.server.shell;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShellStatusUpdaterTest {

    private ShellStatusUpdater shellStatusUpdater;
    private ShellRepository shellRepository;

    @BeforeEach
    void setUp() {
        shellStatusUpdater = new ShellStatusUpdater();
        shellRepository = mock(ShellRepository.class);
        ReflectionTestUtils.setField(shellStatusUpdater, "shellRepository", shellRepository);
    }

    @Test
    void shouldSetLastOnlineAtWhenMarkedConnected() {
        Long shellId = 1L;
        Shell shell = new Shell();
        shell.setId(shellId);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));

        shellStatusUpdater.markConnected(shellId);

        assertEquals(ShellStatus.CONNECTED, shell.getStatus());
        assertNotNull(shell.getLastOnlineAt());
    }

    @Test
    void shouldPreserveLastOnlineAtWhenMarkedError() {
        Long shellId = 2L;
        LocalDateTime previousLastOnlineAt = LocalDateTime.of(2026, 3, 10, 8, 0, 0);
        Shell shell = new Shell();
        shell.setId(shellId);
        shell.setLastOnlineAt(previousLastOnlineAt);
        when(shellRepository.findById(shellId)).thenReturn(Optional.of(shell));

        shellStatusUpdater.markError(shellId);

        assertEquals(ShellStatus.ERROR, shell.getStatus());
        assertEquals(previousLastOnlineAt, shell.getLastOnlineAt());
    }
}
