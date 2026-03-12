package com.reajason.noone.server.admin.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserSessionResponse {
    private Long id;
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private String deviceInfo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime accessExpiresAt;
    private LocalDateTime refreshExpiresAt;
    private boolean revoked;
    private LocalDateTime revokedAt;
    private String revokeReason;
}
