package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.admin.user.dto.UserSessionQueryRequest;
import com.reajason.noone.server.admin.user.dto.UserSessionResponse;
import com.reajason.noone.server.api.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserSession createSession(
            User user,
            String sessionId,
            String refreshTokenId,
            String ipAddress,
            String userAgent,
            LocalDateTime accessExpiresAt,
            LocalDateTime refreshExpiresAt) {
        UserSession session = new UserSession();
        session.setUser(user);
        session.setSessionId(sessionId);
        session.setRefreshTokenHash(hash(refreshTokenId));
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
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
                .filter(session -> session.getAccessExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public UserSession rotateRefreshToken(
            String sessionId,
            String currentRefreshTokenId,
            String newRefreshTokenId,
            LocalDateTime newAccessExpiresAt,
            LocalDateTime newRefreshExpiresAt,
            String ipAddress,
            String userAgent) {
        UserSession session = userSessionRepository.findBySessionIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session was not found"));

        if (session.isRevoked()) {
            throw new IllegalArgumentException("Session has already been revoked");
        }
        if (session.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
            revokeSession(session, "REFRESH_EXPIRED");
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
        return userSessionRepository.save(session);
    }

    @Transactional
    public void touchSession(String sessionId, String ipAddress, String userAgent) {
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
            userSessionRepository.save(session);
        });
    }

    @Transactional
    public void revokeSession(String sessionId, String reason) {
        userSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            revokeSession(session, reason);
        });
    }

    @Transactional
    public void revokeUserSessions(Long userId, String reason) {
        findActiveUser(userId);
        userSessionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .forEach(session -> revokeSession(session, reason));
    }

    @Transactional(readOnly = true)
    public Page<UserSessionResponse> getUserSessions(Long userId, UserSessionQueryRequest request) {
        findActiveUser(userId);

        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<UserSession> spec = Specification.where((root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("user").get("id"), userId));

        if (request.getRevoked() != null) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("revoked"), request.getRevoked()));
        }
        if (request.getCreatedAfter() != null) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), request.getCreatedAfter()));
        }
        if (request.getCreatedBefore() != null) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), request.getCreatedBefore()));
        }

        return userSessionRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional
    public void revokeSession(Long userId, String sessionId, String reason) {
        findActiveUser(userId);
        UserSession session = userSessionRepository.findByUserIdAndSessionId(userId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("用户会话不存在：" + sessionId));
        revokeSession(session, reason);
    }

    private UserSessionResponse toResponse(UserSession session) {
        UserSessionResponse response = new UserSessionResponse();
        response.setId(session.getId());
        response.setSessionId(session.getSessionId());
        response.setIpAddress(session.getIpAddress());
        response.setUserAgent(session.getUserAgent());
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

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在：" + userId));
    }

    private void revokeSession(UserSession session, String reason) {
        if (session.isRevoked()) {
            return;
        }
        session.setRevoked(true);
        session.setRevokedAt(LocalDateTime.now());
        session.setRevokeReason(reason);
        userSessionRepository.save(session);
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
