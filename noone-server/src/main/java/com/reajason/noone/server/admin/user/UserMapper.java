package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.user.dto.UserCreateRequest;
import com.reajason.noone.server.admin.user.dto.UserResponse;
import com.reajason.noone.server.admin.user.dto.UserRoleDTO;
import com.reajason.noone.server.admin.user.dto.UserUpdateRequest;
import jakarta.annotation.Resource;
import org.mapstruct.*;

/**
 * 用户实体与DTO映射器
 *
 * @author ReaJason
 * @since 2025/1/27
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class UserMapper {

    @Resource
    protected UserAuthorityResolver userAuthorityResolver;

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lastLogin", ignore = true)
    @Mapping(target = "lastLoginIp", ignore = true)
    @Mapping(target = "passwordChangedAt", ignore = true)
    @Mapping(target = "mfaBoundAt", ignore = true)
    @Mapping(target = "mfaSecret", ignore = true)
    @Mapping(target = "mfaEnabled", ignore = true)
    @Mapping(target = "mustChangePassword", ignore = true)
    @Mapping(target = "failedAttempts", ignore = true)
    @Mapping(target = "lockTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    public abstract User toEntity(UserCreateRequest request);

    @Mapping(target = "authorities", expression = "java(userAuthorityResolver.resolveAuthorityCodes(user))")
    public abstract UserResponse toResponse(User user);

    public abstract UserRoleDTO toUserRoleDTO(Role role);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lastLogin", ignore = true)
    @Mapping(target = "lastLoginIp", ignore = true)
    @Mapping(target = "passwordChangedAt", ignore = true)
    @Mapping(target = "mfaBoundAt", ignore = true)
    @Mapping(target = "mfaEnabled", ignore = true)
    @Mapping(target = "mfaSecret", ignore = true)
    @Mapping(target = "mustChangePassword", ignore = true)
    @Mapping(target = "failedAttempts", ignore = true)
    @Mapping(target = "lockTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    public abstract void updateEntity(@MappingTarget User user, UserUpdateRequest request);
}
