package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.api.ResourceNotFoundException;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.user.dto.*;
import com.reajason.noone.server.audit.AuditAction;
import com.reajason.noone.server.audit.AuditLog;
import com.reajason.noone.server.audit.AuditModule;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final com.reajason.noone.server.admin.auth.TwoFactorAuthService twoFactorAuthService;
    private final LoginLogRepository loginLogRepository;
    private final UserAuthorityResolver userAuthorityResolver;

    @AuditLog(module = AuditModule.USER, action = AuditAction.CREATE, targetType = "User", targetId = "#result.id")
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByUsernameAndDeletedFalse(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在：" + request.getUsername());
        }

        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setMfaSecret(twoFactorAuthService.generateSecret());
        user.setStatus(UserStatus.UNACTIVATED);
        user.setMustChangePassword(true);
        user.setMfaEnabled(false);

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            Set<Role> roles = roleRepository.findAllById(request.getRoleIds()).stream()
                    .filter(role -> !Boolean.TRUE.equals(role.getDeleted()))
                    .collect(java.util.stream.Collectors.toSet());
            user.setRoles(roles);
        }

        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = findActiveUser(id);
        return userMapper.toResponse(user);
    }

    @AuditLog(module = AuditModule.USER, action = AuditAction.UPDATE, targetType = "User", targetId = "#id")
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = findActiveUser(id);
        userMapper.updateEntity(user, request);

        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

    @AuditLog(module = AuditModule.USER, action = AuditAction.DELETE, targetType = "User", targetId = "#id")
    public void delete(Long id) {
        User user = findActiveUser(id);
        user.setDeleted(Boolean.TRUE);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> query(UserQueryRequest request) {
        UserStatus status = resolveStatusFilter(request);
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortOrder())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                request.getSortBy());

        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        Specification<User> spec = UserSpecifications.hasUsername(request.getUsername())
                .and(UserSpecifications.hasRole(request.getRoleId()))
                .and(UserSpecifications.status(status))
                .and(UserSpecifications.createdAfter(request.getCreatedAfter()))
                .and(UserSpecifications.createdBefore(request.getCreatedBefore()))
                .and(UserSpecifications.notDeleted());

        return userRepository.findAll(spec, pageable).map(userMapper::toResponse);
    }

    @AuditLog(module = AuditModule.USER, action = AuditAction.UPDATE, targetType = "User", targetId = "#id", description = "'Sync roles'")
    public UserResponse syncRoles(Long id, Set<Long> roleIds) {
        User user = findActiveUser(id);
        Set<Role> roles = roleIds == null
                ? new HashSet<>()
                : roleRepository.findAllById(roleIds).stream()
                        .filter(role -> !Boolean.TRUE.equals(role.getDeleted()))
                        .collect(java.util.stream.Collectors.toSet());
        user.setRoles(roles);
        return userMapper.toResponse(userRepository.save(user));
    }

    @AuditLog(module = AuditModule.USER, action = AuditAction.PASSWORD_RESET, targetType = "User", targetId = "#id")
    public UserResponse resetPassword(Long id, ResetPasswordRequest request) {
        User user = findActiveUser(id);

        if (request.getOldPassword() != null) {
            if (request.getOldPassword().isBlank()) {
                throw new IllegalArgumentException("旧密码不能为空");
            }
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new IllegalArgumentException("旧密码不正确");
            }
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("新密码不能与旧密码相同");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(Boolean.TRUE.equals(request.getForceChangeOnNextLogin()));
        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

    @AuditLog(module = AuditModule.USER, action = AuditAction.PASSWORD_RESET, targetType = "User", targetId = "#id", description = "'Force reset password'")
    public UserResponse forceResetPassword(Long id, String temporaryPassword) {
        User user = findActiveUser(id);
        if (passwordEncoder.matches(temporaryPassword, user.getPassword())) {
            throw new IllegalArgumentException("新密码不能与旧密码相同");
        }
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(null);
        return userMapper.toResponse(userRepository.save(user));
    }

    @AuditLog(module = AuditModule.USER, action = AuditAction.PASSWORD_CHANGE, targetType = "User", targetId = "#user.id")
    public UserResponse changePassword(User user, String oldPassword, String newPassword) {
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("旧密码不正确");
        }
        return changePassword(user, newPassword);
    }

    public UserResponse forceChangePassword(String username, String newPassword) {
        User user = getByUsername(username);
        return changePassword(user, newPassword);
    }

    public Set<GrantedAuthority> getAuthorities(String username) {
        User user = getByUsername(username);
        return userAuthorityResolver.resolveGrantedAuthorities(user);
    }

    public User updateLastLogin(String username, String ipAddress) {
        User user = getByUsername(username);
        user.setLastLogin(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
    }

    @Transactional(readOnly = true)
    public List<LoginLogResponse> getLoginLogs(Long userId) {
        return loginLogRepository.findTop50ByUserIdOrderByLoginTimeDesc(userId).stream()
                .map(this::toLoginLogResponse)
                .toList();
    }

    private UserStatus resolveStatusFilter(UserQueryRequest request) {
        if (request.getStatus() != null) {
            return request.getStatus();
        }
        if (request.getEnabled() == null) {
            return null;
        }
        return request.getEnabled() ? UserStatus.ENABLED : UserStatus.DISABLED;
    }

    private UserResponse changePassword(User user, String newPassword) {
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("新密码不能与旧密码相同");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        return userMapper.toResponse(userRepository.save(user));
    }

    private LoginLogResponse toLoginLogResponse(LoginLog log) {
        LoginLogResponse response = new LoginLogResponse();
        response.setId(log.getId());
        response.setUserId(log.getUserId());
        response.setUsername(log.getUsername());
        response.setSessionId(log.getSessionId());
        response.setIpAddress(log.getIpAddress());
        response.setUserAgent(log.getUserAgent());
        response.setBrowser(log.getBrowser());
        response.setOs(log.getOs());
        response.setStatus(log.getStatus().name());
        response.setFailReason(log.getFailReason());
        response.setLoginTime(log.getLoginTime());
        return response;
    }

    private User findActiveUser(Long id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在：" + id));
    }
}
