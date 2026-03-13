package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.admin.user.dto.UserSessionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;

    @Transactional
    public UserSession createSession(
            User user,
            String sessionId,
            String refreshTokenId,
            String ipAddress,
            String userAgent,
            String deviceInfo,
            LocalDateTime accessExpiresAt,
            LocalDateTime refreshExpiresAt) {
        UserSession session = new UserSession();
        session.setUser(user);
        session.setSessionId(sessionId);
        session.setRefreshTokenHash(hash(refreshTokenId));
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        session.setDeviceInfo(deviceInfo);
        session.setLastSeenAt(LocalDateTime.now());
        session.setAccessExpiresAt(accessExpiresAt);
        session.setRefreshExpiresAt(refreshExpiresAt);
        session.setRevoked(false);
        return userSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public boolean isSessionValid(String sessionId) {
        return userSessionRepository.findBySessionId(sessionId)
                .filter(session -> !session.isRevoked())
                .filter(session -> session.getRefreshExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    @Transactional
    public UserSession rotateRefreshToken(
            String sessionId,
            String currentRefreshTokenId,
            String newRefreshTokenId,
            LocalDateTime newAccessExpiresAt,
            LocalDateTime newRefreshExpiresAt,
            String ipAddress,
            String userAgent,
            String deviceInfo) {
        UserSession session = userSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session was not found"));

        if (session.isRevoked()) {
            throw new IllegalArgumentException("Session has already been revoked");
        }
        if (session.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
            revokeSession(sessionId, "REFRESH_EXPIRED");
            throw new IllegalArgumentException("Session refresh token has expired");
        }
//        if (!session.getRefreshTokenHash().equals(hash(currentRefreshTokenId))) {
//            revokeSession(sessionId, "REFRESH_REUSE_DETECTED");
//            throw new IllegalArgumentException("Refresh token reuse detected");
//        }

        session.setRefreshTokenHash(hash(newRefreshTokenId));
        session.setAccessExpiresAt(newAccessExpiresAt);
        session.setRefreshExpiresAt(newRefreshExpiresAt);
        session.setLastSeenAt(LocalDateTime.now());
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        session.setDeviceInfo(deviceInfo);
        return userSessionRepository.save(session);
    }

    @Transactional
    public void touchSession(String sessionId, String ipAddress, String userAgent, String deviceInfo) {
        userSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            if (session.isRevoked()) {
                return;
            }
            session.setLastSeenAt(LocalDateTime.now());
            if (ipAddress != null) {
                session.setIpAddress(ipAddress);
            }
            if (userAgent != null) {
                session.setUserAgent(userAgent);
            }
            if (deviceInfo != null) {
                session.setDeviceInfo(deviceInfo);
            }
            userSessionRepository.save(session);
        });
    }

    @Transactional
    public void revokeSession(String sessionId, String reason) {
        userSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            if (session.isRevoked()) {
                return;
            }
            session.setRevoked(true);
            session.setRevokedAt(LocalDateTime.now());
            session.setRevokeReason(reason);
            userSessionRepository.save(session);
        });
    }

    @Transactional
    public void revokeUserSessions(Long userId, String reason) {
        userSessionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .forEach(session -> revokeSession(session.getSessionId(), reason));
    }

    @Transactional(readOnly = true)
    public List<UserSessionResponse> getUserSessions(Long userId) {
        return userSessionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    private UserSessionResponse toResponse(UserSession session) {
        UserSessionResponse response = new UserSessionResponse();
        response.setId(session.getId());
        response.setSessionId(session.getSessionId());
        response.setIpAddress(session.getIpAddress());
        response.setUserAgent(session.getUserAgent());
        response.setDeviceInfo(session.getDeviceInfo());
        response.setCreatedAt(session.getCreatedAt());
        response.setUpdatedAt(session.getUpdatedAt());
        response.setLastSeenAt(session.getLastSeenAt());
        response.setAccessExpiresAt(session.getAccessExpiresAt());
        response.setRefreshExpiresAt(session.getRefreshExpiresAt());
        response.setRevoked(session.isRevoked());
        response.setRevokedAt(session.getRevokedAt());
        response.setRevokeReason(session.getRevokeReason());
        return response;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
