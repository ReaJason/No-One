package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.user.dto.UserCreateRequest;
import com.reajason.noone.server.admin.user.dto.UserResponse;
import com.reajason.noone.server.admin.user.dto.UserRoleDTO;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 用户实体与DTO映射器
 *
 * @author ReaJason
 * @since 2025/1/27
 */
@Component
public class UserMapper {

    private final UserAuthorityResolver userAuthorityResolver;

    public UserMapper(UserAuthorityResolver userAuthorityResolver) {
        this.userAuthorityResolver = userAuthorityResolver;
    }

    public User toEntity(UserCreateRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setEmail(request.getEmail());
        user.setStatus(request.getStatus() == null ? UserStatus.ENABLED : request.getStatus());
        return user;
    }

    public UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setStatus(user.getStatus());
        response.setMfaEnabled(user.isMfaEnabled());
        response.setMustChangePassword(user.isMustChangePassword());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        response.setLastLogin(user.getLastLogin());
        response.setLastLoginIp(user.getLastLoginIp());
        response.setPasswordChangedAt(user.getPasswordChangedAt());
        response.setMfaBoundAt(user.getMfaBoundAt());
        response.setRoles(user.getRoles().stream().map(this::toUserRoleDTO).collect(Collectors.toSet()));
        response.setAuthorities(userAuthorityResolver.resolveAuthorityCodes(user));
        return response;
    }

    public UserRoleDTO toUserRoleDTO(Role role) {
        UserRoleDTO userRoleDTO = new UserRoleDTO();
        userRoleDTO.setId(role.getId());
        userRoleDTO.setName(role.getName());
        return userRoleDTO;
    }
}
