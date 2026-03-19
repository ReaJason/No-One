package com.reajason.noone.server.admin.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserIpWhitelistResponse {
    private Long id;
    private Long userId;
    private String ipAddress;
    private LocalDateTime createdAt;
}
