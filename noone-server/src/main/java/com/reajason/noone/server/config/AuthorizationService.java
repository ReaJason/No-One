package com.reajason.noone.server.config;

import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.project.Project;
import com.reajason.noone.server.project.ProjectRepository;
import com.reajason.noone.server.project.ProjectSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component("authorizationService")
@RequiredArgsConstructor
public class AuthorizationService {

    private static final String SUPER_ADMIN_ROLE = "SUPER ADMIN";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    public Optional<User> getCurrentUserOptional() {
        if (!isAuthenticated()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public User getCurrentUser() {
        return getCurrentUserOptional()
                .orElseThrow(() -> new IllegalArgumentException("Current user is not available"));
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    public boolean hasSystemPermission(String permissionCode) {
        if (!isAuthenticated()) {
            return false;
        }
        User user = getCurrentUser();
        return isSuperAdmin(user) || collectPermissionCodes(user).contains(permissionCode);
    }

    public boolean isAdmin() {
        return isAuthenticated() && isSuperAdmin(getCurrentUser());
    }

    public boolean canAccessProject(Long projectId) {
        if (!isAuthenticated() || projectId == null) {
            return false;
        }
        User user = getCurrentUser();
        if (isSuperAdmin(user)) {
            return true;
        }
        return projectRepository.exists(ProjectSpecifications.isOwnerOrMember(projectId, user));
    }

    public Set<Long> getVisibleProjectIds() {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) {
            return projectRepository.findAll().stream().map(Project::getId).collect(Collectors.toSet());
        }
        return projectRepository.findAll(ProjectSpecifications.isOwnerOrMember(user)).stream()
                .map(Project::getId)
                .collect(Collectors.toSet());
    }

    public Set<String> collectPermissionCodes(User user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .map(String::toUpperCase)
                .anyMatch(roleName -> roleName.equals(SUPER_ADMIN_ROLE))
                || user.getRoles().stream()
                .map(Role::getName)
                .anyMatch("Super Admin"::equalsIgnoreCase);
    }
}
