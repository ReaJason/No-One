package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.admin.user.dto.*;
import com.reajason.noone.server.api.ErrorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserSessionService userSessionService;

    @PostMapping
    @PreAuthorize("@authorizationService.hasSystemPermission('user:create')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('user:read')")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("@authorizationService.hasSystemPermission('user:update')")
    public ResponseEntity<UserResponse> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        UserResponse response = userService.resetPassword(id, request);
        userSessionService.revokeUserSessions(id, "ADMIN_PASSWORD_RESET");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('user:delete')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id) {
        userSessionService.revokeUserSessions(id, "ADMIN_USER_DELETED");
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('user:update')")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasSystemPermission('user:list')")
    public ResponseEntity<Page<UserResponse>> query(UserQueryRequest request) {
        return ResponseEntity.ok(userService.query(request));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("@authorizationService.hasSystemPermission('user:update')")
    public ResponseEntity<UserResponse> syncRoles(
            @PathVariable Long id,
            @RequestBody Set<Long> roleIds) {
        return ResponseEntity.ok(userService.syncRoles(id, roleIds));
    }

    @GetMapping("/{id}/login-logs")
    @PreAuthorize("@authorizationService.hasSystemPermission('auth:log:read')")
    public ResponseEntity<Page<LoginLogResponse>> loginLogs(
            @PathVariable Long id,
            LoginLogQueryRequest request) {
        return ResponseEntity.ok(userService.getLoginLogs(id, request));
    }

    @GetMapping("/{id}/sessions")
    @PreAuthorize("@authorizationService.hasSystemPermission('auth:session:manage')")
    public ResponseEntity<Page<UserSessionResponse>> sessions(
            @PathVariable Long id,
            UserSessionQueryRequest request) {
        return ResponseEntity.ok(userSessionService.getUserSessions(id, request));
    }

    @DeleteMapping("/{id}/sessions")
    @PreAuthorize("@authorizationService.hasSystemPermission('auth:session:manage')")
    public ResponseEntity<Void> revokeAllSessions(
            @PathVariable Long id) {
        userSessionService.revokeUserSessions(id, "ADMIN_FORCE_LOGOUT");
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    @PreAuthorize("@authorizationService.hasSystemPermission('auth:session:manage')")
    public ResponseEntity<Void> revokeSession(
            @PathVariable Long id,
            @PathVariable String sessionId) {
        userSessionService.revokeSession(id, sessionId, "ADMIN_FORCE_LOGOUT");
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause == null ? ex.getMessage() : cause.getMessage();
        return ResponseEntity.badRequest().body(ErrorResponse.error(message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, String>> handleBindException(BindException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            String errorMessage = error.getDefaultMessage() == null ? error.toString() : error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.error(ex.getMessage()));
    }
}
