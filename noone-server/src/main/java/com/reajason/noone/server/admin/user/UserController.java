package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.admin.user.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserSessionService userSessionService;
    private final com.reajason.noone.server.admin.auth.SensitiveActionService sensitiveActionService;

    @PostMapping
    @PreAuthorize("@authorizationService.hasSystemPermission('user:create')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('user:read')")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        log.info("getting user by id: {}", id);
        return ResponseEntity.ok(userService.getById(id));
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("@authorizationService.hasSystemPermission('user:update')")
    public ResponseEntity<UserResponse> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request,
            @RequestHeader(value = com.reajason.noone.server.admin.auth.SensitiveActionService.CHALLENGE_HEADER, required = false) String challengeToken) {
        log.info("resetting password for user id: {}", id);
        sensitiveActionService.requireChallenge(challengeToken, "user.reset-password", "user", String.valueOf(id));
        UserResponse response = userService.forceResetPassword(id, request.getNewPassword());
        userSessionService.revokeUserSessions(id, "ADMIN_PASSWORD_RESET");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('user:delete')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = com.reajason.noone.server.admin.auth.SensitiveActionService.CHALLENGE_HEADER, required = false) String challengeToken) {
        sensitiveActionService.requireChallenge(challengeToken, "user.delete", "user", String.valueOf(id));
        userSessionService.revokeUserSessions(id, "ADMIN_USER_DELETED");
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('user:update')")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request,
            @RequestHeader(value = com.reajason.noone.server.admin.auth.SensitiveActionService.CHALLENGE_HEADER, required = false) String challengeToken) {
        log.info("Updating user with id: {}", id);
        if (request.getStatus() != null || request.getRoleIds() != null) {
            sensitiveActionService.requireChallenge(challengeToken, "user.update-security", "user", String.valueOf(id));
        }
        UserResponse response = userService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasSystemPermission('user:list')")
    public ResponseEntity<Page<UserResponse>> query(UserQueryRequest request) {
        return ResponseEntity.ok(userService.query(request));
    }

    @GetMapping("/{id}/login-logs")
    @PreAuthorize("@authorizationService.hasSystemPermission('auth:log:read')")
    public ResponseEntity<List<LoginLogResponse>> loginLogs(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getLoginLogs(id));
    }

    @GetMapping("/{id}/sessions")
    @PreAuthorize("@authorizationService.hasSystemPermission('auth:session:manage')")
    public ResponseEntity<List<UserSessionResponse>> sessions(@PathVariable Long id) {
        return ResponseEntity.ok(userSessionService.getUserSessions(id));
    }

    @DeleteMapping("/{id}/sessions")
    @PreAuthorize("@authorizationService.hasSystemPermission('auth:session:manage')")
    public ResponseEntity<Void> revokeAllSessions(
            @PathVariable Long id,
            @RequestHeader(value = com.reajason.noone.server.admin.auth.SensitiveActionService.CHALLENGE_HEADER, required = false) String challengeToken) {
        sensitiveActionService.requireChallenge(challengeToken, "user.revoke-all-sessions", "user", String.valueOf(id));
        userSessionService.revokeUserSessions(id, "ADMIN_FORCE_LOGOUT");
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    @PreAuthorize("@authorizationService.hasSystemPermission('auth:session:manage')")
    public ResponseEntity<Void> revokeSession(
            @PathVariable Long id,
            @PathVariable String sessionId,
            @RequestHeader(value = com.reajason.noone.server.admin.auth.SensitiveActionService.CHALLENGE_HEADER, required = false) String challengeToken) {
        sensitiveActionService.requireChallenge(challengeToken, "user.revoke-session", "session", sessionId);
        userSessionService.revokeSession(sessionId, "ADMIN_FORCE_LOGOUT");
        return ResponseEntity.noContent().build();
    }
}
