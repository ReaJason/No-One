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

    public User toEntity(UserCreateRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setEnabled(request.isEnabled());
        return user;
    }

    public UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEnabled(user.isEnabled());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        response.setRoles(user.getRoles().stream().map(this::toUserRoleDTO).collect(Collectors.toSet()));
        return response;
    }

    public UserRoleDTO toUserRoleDTO(Role role) {
        UserRoleDTO userRoleDTO = new UserRoleDTO();
        userRoleDTO.setId(role.getId());
        userRoleDTO.setName(role.getName());
        return userRoleDTO;
    }
}
