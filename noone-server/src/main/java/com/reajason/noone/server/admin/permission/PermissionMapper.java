package com.reajason.noone.server.admin.permission;

import com.reajason.noone.server.admin.permission.dto.PermissionCreateRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionResponse;
import com.reajason.noone.server.admin.permission.dto.PermissionRoleDTO;
import com.reajason.noone.server.admin.permission.dto.PermissionUpdateRequest;
import com.reajason.noone.server.admin.role.Role;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PermissionMapper {

    public Permission toEntity(PermissionCreateRequest request) {
        return Permission.builder()
                .code(request.getCode())
                .name(request.getName())
                .build();
    }

    public PermissionResponse toResponse(Permission permission) {
        PermissionResponse response = new PermissionResponse();
        response.setId(permission.getId());
        response.setCode(permission.getCode());
        response.setName(permission.getName());
        response.setCategory(extractCategory(permission.getCode()));
        response.setRoles(permission.getRoles().stream().map(this::toPermissionRoleDTO).collect(Collectors.toSet()));
        response.setCreatedAt(permission.getCreatedAt());
        response.setUpdatedAt(permission.getUpdatedAt());
        return response;
    }

    public PermissionRoleDTO toPermissionRoleDTO(Role role) {
        PermissionRoleDTO permissionRoleDTO = new PermissionRoleDTO();
        permissionRoleDTO.setId(role.getId());
        permissionRoleDTO.setName(role.getName());
        return permissionRoleDTO;
    }

    public static String extractCategory(String code) {
        return code.split(":")[0].toLowerCase();
    }

    public void updateEntity(Permission permission, PermissionUpdateRequest request) {
        if (request.getCode() != null) {
            permission.setCode(request.getCode());
        }
        if (request.getName() != null) {
            permission.setName(request.getName());
        }
    }
}
