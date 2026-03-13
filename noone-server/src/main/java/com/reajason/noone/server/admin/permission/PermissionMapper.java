package com.reajason.noone.server.admin.permission;

import com.reajason.noone.server.admin.permission.dto.PermissionCreateRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionResponse;
import com.reajason.noone.server.admin.permission.dto.PermissionRoleDTO;
import com.reajason.noone.server.admin.permission.dto.PermissionUpdateRequest;
import com.reajason.noone.server.admin.role.Role;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class PermissionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract Permission toEntity(PermissionCreateRequest request);

    @Mapping(target = "category", expression = "java(extractCategory(permission.getCode()))")
    public abstract PermissionResponse toResponse(Permission permission);

    public abstract PermissionRoleDTO toPermissionRoleDTO(Role role);

    public abstract void updateEntity(@MappingTarget Permission permission, PermissionUpdateRequest request);

    protected static String extractCategory(String code) {
        return code.split(":")[0].toLowerCase();
    }
}
