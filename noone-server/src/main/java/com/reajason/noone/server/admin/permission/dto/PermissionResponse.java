package com.reajason.noone.server.admin.permission.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class PermissionResponse {
    private Long id;
    private String code;
    private String name;
    private String category;
    private Set<PermissionRoleDTO> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
