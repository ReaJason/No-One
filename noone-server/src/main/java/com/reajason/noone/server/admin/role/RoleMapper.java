package com.reajason.noone.server.admin.role;

import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.role.dto.RoleCreateRequest;
import com.reajason.noone.server.admin.role.dto.RolePermissionResponse;
import com.reajason.noone.server.admin.role.dto.RoleResponse;
import com.reajason.noone.server.admin.role.dto.RoleUpdateRequest;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class RoleMapper {

    public Role toEntity(RoleCreateRequest request) {
        return Role.builder()
                .name(request.getName())
                .build();
    }

    public RoleResponse toResponse(Role role) {
        RoleResponse response = new RoleResponse();
        response.setId(role.getId());
        response.setName(role.getName());
        response.setPermissions(role.getPermissions().stream().map(this::toPermissionResponse).collect(Collectors.toSet()));
        response.setCreatedAt(role.getCreatedAt());
        response.setUpdatedAt(role.getUpdatedAt());
        return response;
    }

    public RolePermissionResponse toPermissionResponse(Permission permission) {
        RolePermissionResponse permissionResponse = new RolePermissionResponse();
        permissionResponse.setId(permission.getId());
        permissionResponse.setCode(permission.getCode());
        permissionResponse.setName(permission.getName());
        return permissionResponse;
    }

    public void updateEntity(Role role, RoleUpdateRequest request) {
        if (request.getName() != null) {
            role.setName(request.getName());
        }
    }
}
