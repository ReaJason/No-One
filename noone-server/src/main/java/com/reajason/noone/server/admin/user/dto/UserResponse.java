package com.reajason.noone.server.admin.user.dto;

import com.reajason.noone.server.admin.user.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private UserStatus status;
    private boolean mfaEnabled;
    private boolean mustChangePassword;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLogin;
    private String lastLoginIp;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime mfaBoundAt;
    private Set<UserRoleDTO> roles;
    private Set<String> authorities;
}
