package com.reajason.noone.server.shell;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reajason.noone.server.profile.ProfileRepository;
import com.reajason.noone.server.shell.dto.ShellResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ShellMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ShellMapper shellMapper;

    @BeforeEach
    void setUp() {
        shellMapper = new ShellMapper();
        ReflectionTestUtils.setField(shellMapper, "profileRepository", mock(ProfileRepository.class));
    }

    @Test
    void shouldMapAndSerializeShellTimestampsWithCreatedAtAndUpdatedAt() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 11, 9, 0, 0);
        LocalDateTime lastOnlineAt = LocalDateTime.of(2026, 3, 12, 10, 30, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 12, 10, 31, 0);
        Shell shell = new Shell();
        shell.setId(1L);
        shell.setUrl("http://127.0.0.1/test");
        shell.setLanguage(ShellLanguage.JAVA);
        shell.setStatus(ShellStatus.CONNECTED);
        shell.setCreatedAt(createdAt);
        shell.setLastOnlineAt(lastOnlineAt);
        shell.setUpdatedAt(updatedAt);

        ShellResponse response = shellMapper.toResponse(shell);
        String json = objectMapper.writeValueAsString(response);

        assertEquals(createdAt, response.getCreatedAt());
        assertEquals(lastOnlineAt, response.getLastOnlineAt());
        assertEquals(updatedAt, response.getUpdatedAt());
        assertTrue(json.contains("\"createdAt\""));
        assertTrue(json.contains("\"lastOnlineAt\""));
        assertTrue(json.contains("\"updatedAt\""));
        assertFalse(json.contains("\"createTime\""));
        assertFalse(json.contains("\"connectTime\""));
        assertFalse(json.contains("\"updateTime\""));
    }
}
