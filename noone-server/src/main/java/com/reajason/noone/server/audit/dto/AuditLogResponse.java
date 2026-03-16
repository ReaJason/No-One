package com.reajason.noone.server.audit.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AuditLogResponse {
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String targetType;
    private String targetId;
    private String description;
    private boolean success;
    private String errorMessage;
    private Long durationMs;
    private String ipAddress;
    private String userAgent;
    private String requestMethod;
    private String requestUri;
    private Map<String, Object> details;
    private LocalDateTime createdAt;
}
