package com.reajason.noone.server.admin.auth;

import com.reajason.noone.server.admin.auth.dto.*;
import com.reajason.noone.server.admin.user.*;
import com.reajason.noone.server.admin.user.dto.UserResponse;
import com.reajason.noone.server.config.AuthorizationService;
import com.reajason.noone.server.util.IpUtils;
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

    private static final String LOGIN_2FA_CHALLENGE_TYPE = "login_2fa";
    private static final String LOGIN_2FA_CHALLENGE_ACTION = "LOGIN_2FA_VERIFY";

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
    private final TwoFactorAuthService twoFactorAuthService;

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest loginRequest,
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
                    null));
            User latestUser = userService.getByUsername(loginRequest.getUsername());
            if (latestUser.isMustChangePassword()) {
                return requirePasswordChange(latestUser, request);
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
            String challengeToken = jwtUtil.generateActionToken(
                    user.getUsername(),
                    jwtUtil.newTokenId(),
                    LOGIN_2FA_CHALLENGE_TYPE,
                    LOGIN_2FA_CHALLENGE_ACTION,
                    null,
                    null,
                    jwtUtil.getJwtConfig().getChallengeExpiration());
            recordLogin(user, null, request, LoginLog.LoginStatus.REQUIRE_2FA, null);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new LoginErrorResponse(
                    "REQUIRE_2FA",
                    "Enter your authenticator code to continue",
                    user.getStatus().name(),
                    true,
                    null,
                    challengeToken));
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

    @PostMapping("/verify-2fa")
    public ResponseEntity<?> verifyTwoFactor(
            @Valid @RequestBody VerifyTwoFactorRequest verifyRequest,
            HttpServletRequest request) {
        if (!isValidLoginTwoFactorChallenge(verifyRequest.getActionToken())) {
            return invalidTwoFactorChallenge();
        }

        Optional<User> optionalUser = userRepository.findByUsernameAndDeletedFalse(
                jwtUtil.getUsernameFromToken(verifyRequest.getActionToken()));
        if (optionalUser.isEmpty()) {
            return invalidTwoFactorChallenge();
        }

        User user = optionalUser.get();
        Optional<AuthPolicyDecision> authPolicyDecision = authPolicy.evaluate(user);
        if (authPolicyDecision.isPresent()) {
            return policyRejected(user, authPolicyDecision.get(), request);
        }

        if (!user.isMfaEnabled() || user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            recordLogin(user, null, request, LoginLog.LoginStatus.INVALID_CREDENTIALS, "MFA not configured");
            return ResponseEntity.badRequest().body(new LoginErrorResponse(
                    "INVALID_2FA_CODE",
                    "Two-factor authentication is not configured",
                    user.getStatus().name(),
                    true));
        }

        if (!twoFactorAuthService.isCodeValid(user.getMfaSecret(), verifyRequest.getTwoFactorCode())) {
            recordLogin(user, null, request, LoginLog.LoginStatus.REQUIRE_2FA, "Invalid 2FA code");
            return ResponseEntity.badRequest().body(new LoginErrorResponse(
                    "INVALID_2FA_CODE",
                    "Invalid verification code",
                    user.getStatus().name(),
                    true));
        }

        User latestUser = userService.getByUsername(user.getUsername());
        if (latestUser.isMustChangePassword()) {
            return requirePasswordChange(latestUser, request);
        }

        LoginResponse response = issueSession(latestUser, request, "NONE", null);
        recordLogin(latestUser, response.getSessionId(), request, LoginLog.LoginStatus.SUCCESS, null);
        return ResponseEntity.ok(response);
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
            userSessionService.rotateRefreshToken(
                    sessionId,
                    currentRefreshTokenId,
                    newRefreshTokenId,
                    LocalDateTime.now().plus(jwtUtil.getJwtConfig().getExpiration()),
                    LocalDateTime.now().plus(jwtUtil.getJwtConfig().getRefreshExpiration()),
                    IpUtils.getIpAddr(request),
                    request.getHeader("User-Agent"));
            String authorities = userService.getAuthorities(user.getUsername()).stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));
            String accessToken = jwtUtil.generateAccessToken(user.getUsername(), authorities, sessionId, jwtUtil.newTokenId());
            String refreshTokenValue = jwtUtil.generateRefreshToken(user.getUsername(), sessionId, newRefreshTokenId);
            userSessionService.touchSession(sessionId, IpUtils.getIpAddr(request), request.getHeader("User-Agent"));
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

    private ResponseEntity<LoginErrorResponse> requirePasswordChange(User user, HttpServletRequest request) {
        String actionToken = jwtUtil.generatePasswordChangeToken(user.getUsername());
        recordLogin(user, null, request, LoginLog.LoginStatus.REQUIRE_PASSWORD_CHANGE, null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new LoginErrorResponse(
                "REQUIRE_PASSWORD_CHANGE",
                "Password change required before access is granted",
                user.getStatus().name(),
                false,
                null,
                actionToken));
    }

    private ResponseEntity<LoginErrorResponse> invalidTwoFactorChallenge() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new LoginErrorResponse(
                "INVALID_2FA_CHALLENGE",
                "Two-factor challenge is invalid or expired",
                null,
                true));
    }

    private boolean isValidLoginTwoFactorChallenge(String actionToken) {
        if (actionToken == null || actionToken.isBlank()) {
            return false;
        }
        try {
            return jwtUtil.validateToken(actionToken)
                    && jwtUtil.isTokenNotExpired(actionToken)
                    && LOGIN_2FA_CHALLENGE_TYPE.equals(jwtUtil.getTokenType(actionToken));
        } catch (Exception e) {
            log.warn("Invalid login 2FA challenge token: {}", e.getMessage());
            return false;
        }
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

        User latestUser = userService.updateLastLogin(user.getUsername(), IpUtils.getIpAddr(request));
        if (latestUser.isMfaEnabled() && latestUser.getMfaBoundAt() == null) {
            latestUser.setMfaBoundAt(LocalDateTime.now());
            userRepository.save(latestUser);
        }
        userSessionService.createSession(
                latestUser,
                sessionId,
                refreshTokenId,
                IpUtils.getIpAddr(request),
                request.getHeader("User-Agent"),
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
                .ipAddress(IpUtils.getIpAddr(request))
                .userAgent(request.getHeader("User-Agent"))
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
