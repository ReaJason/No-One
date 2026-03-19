package com.reajason.noone.server.admin.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceTest {

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserSessionService userSessionService;

    @Test
    void shouldTreatSessionAsInvalidWhenAccessTokenExpired() {
        UserSession expiredAccessSession = UserSession.builder()
                .sessionId("expired-access-session")
                .refreshTokenHash(hash("refresh-token-id"))
                .accessExpiresAt(LocalDateTime.now().minusMinutes(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build();
        when(userSessionRepository.findBySessionId("expired-access-session"))
                .thenReturn(Optional.of(expiredAccessSession));

        assertThat(userSessionService.isSessionValid("expired-access-session")).isFalse();
    }

//    @Test
//    void shouldRejectRefreshReuseAndRevokeSession() {
//        UserSession session = UserSession.builder()
//                .sessionId("session-1")
//                .refreshTokenHash(hash("expected-refresh-id"))
//                .accessExpiresAt(LocalDateTime.now().plusMinutes(30))
//                .refreshExpiresAt(LocalDateTime.now().plusDays(1))
//                .revoked(false)
//                .build();
//        when(userSessionRepository.findBySessionIdForUpdate("session-1")).thenReturn(Optional.of(session));
//        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
//
//        assertThatThrownBy(() -> userSessionService.rotateRefreshToken(
//                "session-1",
//                "wrong-refresh-id",
//                "new-refresh-id",
//                LocalDateTime.now().plusMinutes(30),
//                LocalDateTime.now().plusDays(1),
//                "127.0.0.1",
//                "ua"))
//                .isInstanceOf(IllegalArgumentException.class);
//
//        assertThat(session.isRevoked()).isTrue();
//        assertThat(session.getRevokeReason()).isEqualTo("REFRESH_REUSE_DETECTED");
//        verify(userSessionRepository).findBySessionIdForUpdate("session-1");
//        verify(userSessionRepository, never()).findBySessionId("session-1");
//    }

    @Test
    void shouldUseLockedLookupWhenRotatingRefreshToken() {
        UserSession session = UserSession.builder()
                .sessionId("session-2")
                .refreshTokenHash(hash("expected-refresh-id"))
                .accessExpiresAt(LocalDateTime.now().plusMinutes(30))
                .refreshExpiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build();
        when(userSessionRepository.findBySessionIdForUpdate("session-2")).thenReturn(Optional.of(session));
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userSessionService.rotateRefreshToken(
                "session-2",
                "expected-refresh-id",
                "new-refresh-id",
                LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now().plusDays(1),
                "127.0.0.1",
                "ua");

        verify(userSessionRepository).findBySessionIdForUpdate("session-2");
        verify(userSessionRepository, never()).findBySessionId("session-2");
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
