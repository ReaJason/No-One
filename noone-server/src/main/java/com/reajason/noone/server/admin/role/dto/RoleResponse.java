package com.reajason.noone.server.admin.role.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class RoleResponse {
    private Long id;
    private String name;
    private Set<RolePermissionResponse> permissions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
