package com.reajason.noone.server.admin.auth;

import com.reajason.noone.server.admin.auth.dto.*;
import com.reajason.noone.server.admin.user.*;
import com.reajason.noone.server.admin.user.dto.UserResponse;
import com.reajason.noone.server.config.AuthorizationService;
import com.reajason.noone.server.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserService userService;
    private final AuthPolicy authPolicy;
    private final UserSessionService userSessionService;
    private final LoginLogRepository loginLogRepository;
    private final ClientMetadataResolver clientMetadataResolver;
    private final AuthorizationService authorizationService;

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginTwoFactorRequest loginRequest,
            HttpServletRequest request) {
        Optional<User> optionalUser = userRepository.findByUsernameAndDeletedFalse(loginRequest.getUsername());
        if (optionalUser.isEmpty()) {
            return invalidCredentials(loginRequest.getUsername(), request, null);
        }

        User user = optionalUser.get();
        Optional<AuthPolicyDecision> authPolicyDecision = authPolicy.evaluate(user);
        if (authPolicyDecision.isPresent()) {
            return policyRejected(user, authPolicyDecision.get(), request);
        }

        try {
            authenticationManager.authenticate(new TwoFactorAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword(),
                    loginRequest.getTwoFactorCode()));
            User latestUser = userService.getByUsername(loginRequest.getUsername());
            if (latestUser.isMustChangePassword()) {
                String actionToken = jwtUtil.generatePasswordChangeToken(latestUser.getUsername());
                recordLogin(latestUser, null, request, LoginLog.LoginStatus.REQUIRE_PASSWORD_CHANGE, null);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new LoginErrorResponse(
                        "REQUIRE_PASSWORD_CHANGE",
                        "Password change required before access is granted",
                        latestUser.getStatus().name(),
                        false,
                        null,
                        actionToken));
            }
            LoginResponse response = issueSession(latestUser, request, "NONE", null);
            recordLogin(latestUser, response.getSessionId(), request, LoginLog.LoginStatus.SUCCESS, null);
            return ResponseEntity.ok(response);
        } catch (UserNotActivatedException e) {
            recordLogin(user, null, request, LoginLog.LoginStatus.REQUIRE_SETUP, null);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new LoginErrorResponse(
                    "REQUIRE_SETUP",
                    "Account setup required",
                    user.getStatus().name(),
                    false,
                    e.getSetupToken(),
                    null));
        } catch (TwoFactorRequiredException e) {
            recordLogin(user, null, request, LoginLog.LoginStatus.REQUIRE_2FA, null);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new LoginErrorResponse(
                    "REQUIRE_2FA",
                    "Enter your authenticator code to continue",
                    user.getStatus().name(),
                    true));
        } catch (InvalidTwoFactorCodeException e) {
            recordLogin(user, null, request, LoginLog.LoginStatus.REQUIRE_2FA, e.getMessage());
            return ResponseEntity.badRequest().body(new LoginErrorResponse(
                    "INVALID_2FA_CODE",
                    e.getMessage(),
                    user.getStatus().name(),
                    true));
        } catch (AuthenticationException e) {
            recordLogin(
                    user,
                    null,
                    request,
                    LoginLog.LoginStatus.INVALID_CREDENTIALS,
                    e.getMessage());
            return ResponseEntity.badRequest().body(new LoginErrorResponse(
                    "INVALID_CREDENTIALS",
                    e.getMessage() == null ? "Invalid username or password" : e.getMessage(),
                    user.getStatus().name(),
                    false));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest requestBody,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        String refreshToken = resolveToken(authHeader, requestBody == null ? null : requestBody.getRefreshToken());
        if (refreshToken == null || !jwtUtil.validateToken(refreshToken) || !jwtUtil.isTokenNotExpired(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new LoginResponse());
        }
        if (!"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new LoginResponse());
        }

        String sessionId = jwtUtil.getSessionId(refreshToken);
        String currentRefreshTokenId = jwtUtil.getTokenId(refreshToken);
        User user = userService.getByUsername(jwtUtil.getUsernameFromToken(refreshToken));
        try {
            String newRefreshTokenId = jwtUtil.newTokenId();
            ClientMetadata metadata = clientMetadataResolver.resolve(request.getHeader("User-Agent"));
            userSessionService.rotateRefreshToken(
                    sessionId,
                    currentRefreshTokenId,
                    newRefreshTokenId,
                    LocalDateTime.now().plus(jwtUtil.getJwtConfig().getExpiration()),
                    LocalDateTime.now().plus(jwtUtil.getJwtConfig().getRefreshExpiration()),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    metadata.deviceInfo());

            String authorities = userService.getAuthorities(user.getUsername()).stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));
            String accessToken = jwtUtil.generateAccessToken(user.getUsername(), authorities, sessionId, jwtUtil.newTokenId());
            String refreshTokenValue = jwtUtil.generateRefreshToken(user.getUsername(), sessionId, newRefreshTokenId);
            userSessionService.touchSession(sessionId, request.getRemoteAddr(), request.getHeader("User-Agent"), metadata.deviceInfo());
            return ResponseEntity.ok(buildLoginResponse(accessToken, refreshTokenValue, userMapper.toResponse(user), sessionId, "NONE", null));
        } catch (IllegalArgumentException e) {
            log.warn("Refresh token rejected for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new LoginResponse());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<LoginResponse> logout(
            @RequestBody(required = false) RefreshTokenRequest requestBody,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = resolveToken(authHeader, requestBody == null ? null : requestBody.getRefreshToken());
        if (token != null && jwtUtil.validateToken(token)) {
            String sessionId = jwtUtil.getSessionId(token);
            if (sessionId != null) {
                userSessionService.revokeSession(sessionId, "LOGOUT");
            }
        }
        return ResponseEntity.ok(new LoginResponse());
    }

    @GetMapping("/me")
    public ResponseEntity<AuthMeResponse> me() {
        User user = authorizationService.getCurrentUser();
        return ResponseEntity.ok(new AuthMeResponse(userMapper.toResponse(user), true));
    }

    @PostMapping("/change-password")
    public ResponseEntity<UserResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        User currentUser = authorizationService.getCurrentUser();
        UserResponse response = userService.changePassword(currentUser, request.getOldPassword(), request.getNewPassword());
        userSessionService.revokeUserSessions(currentUser.getId(), "PASSWORD_CHANGED");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/password/change-required")
    public ResponseEntity<LoginResponse> completeRequiredPasswordChange(
            @RequestHeader("Password-Change-Token") String passwordChangeToken,
            @Valid @RequestBody ForcePasswordChangeRequest request,
            HttpServletRequest servletRequest) {
        if (!jwtUtil.validateToken(passwordChangeToken) || !jwtUtil.isTokenNotExpired(passwordChangeToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new LoginResponse());
        }
        if (!"password_change".equals(jwtUtil.getTokenType(passwordChangeToken))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new LoginResponse());
        }

        UserResponse updated = userService.forceChangePassword(
                jwtUtil.getUsernameFromToken(passwordChangeToken),
                request.getNewPassword());
        User user = userService.getByUsername(updated.getUsername());
        LoginResponse response = issueSession(user, servletRequest, "NONE", null);
        recordLogin(user, response.getSessionId(), servletRequest, LoginLog.LoginStatus.SUCCESS, null);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<LoginErrorResponse> policyRejected(User user, AuthPolicyDecision decision, HttpServletRequest request) {
        LoginLog.LoginStatus status = switch (decision.code()) {
            case "USER_LOCKED" -> LoginLog.LoginStatus.LOCKED;
            case "USER_DISABLED" -> LoginLog.LoginStatus.DISABLED;
            default -> LoginLog.LoginStatus.INVALID_CREDENTIALS;
        };
        recordLogin(user, null, request, status, decision.message());
        return ResponseEntity.status(decision.httpStatus()).body(new LoginErrorResponse(
                decision.code(),
                decision.message(),
                user.getStatus().name(),
                false));
    }

    private ResponseEntity<LoginErrorResponse> invalidCredentials(String username, HttpServletRequest request, String reason) {
        recordLogin(null, null, request, LoginLog.LoginStatus.INVALID_CREDENTIALS, reason);
        return ResponseEntity.badRequest().body(new LoginErrorResponse(
                "INVALID_CREDENTIALS",
                reason == null ? "Invalid username or password" : reason,
                null,
                false));
    }

    private LoginResponse issueSession(User user, HttpServletRequest request, String nextAction, String actionToken) {
        String sessionId = jwtUtil.newTokenId();
        String accessTokenId = jwtUtil.newTokenId();
        String refreshTokenId = jwtUtil.newTokenId();
        String authorities = userService.getAuthorities(user.getUsername()).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        String accessToken = jwtUtil.generateAccessToken(user.getUsername(), authorities, sessionId, accessTokenId);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername(), sessionId, refreshTokenId);

        ClientMetadata metadata = clientMetadataResolver.resolve(request.getHeader("User-Agent"));
        User latestUser = userService.updateLastLogin(user.getUsername(), request.getRemoteAddr());
        if (latestUser.isMfaEnabled() && latestUser.getMfaBoundAt() == null) {
            latestUser.setMfaBoundAt(LocalDateTime.now());
            userRepository.save(latestUser);
        }
        userSessionService.createSession(
                latestUser,
                sessionId,
                refreshTokenId,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                metadata.deviceInfo(),
                LocalDateTime.now().plus(jwtUtil.getJwtConfig().getExpiration()),
                LocalDateTime.now().plus(jwtUtil.getJwtConfig().getRefreshExpiration()));

        return buildLoginResponse(accessToken, refreshToken, userMapper.toResponse(latestUser), sessionId, nextAction, actionToken);
    }

    private LoginResponse buildLoginResponse(
            String accessToken,
            String refreshToken,
            UserResponse user,
            String sessionId,
            String nextAction,
            String actionToken) {
        LoginResponse response = new LoginResponse();
        response.setToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setUser(user);
        response.setMfaRequired(false);
        response.setExpiresIn(jwtUtil.getJwtConfig().getExpiration().toSeconds());
        response.setSessionId(sessionId);
        response.setNextAction(nextAction);
        response.setActionToken(actionToken);
        return response;
    }

    private void recordLogin(
            User user,
            String sessionId,
            HttpServletRequest request,
            LoginLog.LoginStatus status,
            String failReason) {
        ClientMetadata metadata = clientMetadataResolver.resolve(request.getHeader("User-Agent"));
        loginLogRepository.save(LoginLog.builder()
                .userId(user == null ? null : user.getId())
                .username(user == null ? "unknown" : user.getUsername())
                .sessionId(sessionId)
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .deviceInfo(metadata.deviceInfo())
                .browser(metadata.browser())
                .os(metadata.os())
                .status(status)
                .failReason(failReason)
                .build());
    }

    private String resolveToken(String authHeader, String fallback) {
        String headerToken = jwtUtil.getTokenFromHeader(authHeader);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }
}
