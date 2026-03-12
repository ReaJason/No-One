package com.reajason.noone.server.admin.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginLogResponse {
    private Long id;
    private Long userId;
    private String username;
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private String deviceInfo;
    private String browser;
    private String os;
    private String status;
    private String failReason;
    private LocalDateTime loginTime;
}
