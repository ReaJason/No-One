package com.reajason.noone.server.admin.auth;

import com.reajason.noone.server.admin.auth.dto.LoginResponse;
import com.reajason.noone.server.admin.auth.dto.SetupCodeRequest;
import com.reajason.noone.server.admin.user.*;
import com.reajason.noone.server.admin.user.dto.UserResponse;
import com.reajason.noone.server.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class AuthSetupController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final TwoFactorAuthService twoFactorAuthService;
    private final UserMapper userMapper;
    private final UserService userService;
    private final UserSessionService userSessionService;
    private final ClientMetadataResolver clientMetadataResolver;
    private final LoginLogRepository loginLogRepository;

    @GetMapping("/2fa/qr")
    public ResponseEntity<?> getQrCode(@RequestHeader("Setup-Token") String setupToken) {
        if (!isValidSetupToken(setupToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired setup token");
        }

        User user = userService.getByUsername(jwtUtil.getUsernameFromToken(setupToken));
        if (user.getStatus() != UserStatus.UNACTIVATED) {
            return ResponseEntity.badRequest().body("User is already activated or does not exist");
        }
        return ResponseEntity.ok(Collections.singletonMap(
                "qrCodeUri",
                twoFactorAuthService.getQrCodeImageUri(user.getMfaSecret(), user.getUsername())));
    }

    @PostMapping("/activate")
    public ResponseEntity<?> activateUser(
            @RequestHeader("Setup-Token") String setupToken,
            @Valid @RequestBody SetupCodeRequest request,
            HttpServletRequest servletRequest) {
        if (!isValidSetupToken(setupToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired setup token");
        }

        String username = jwtUtil.getUsernameFromToken(setupToken);
        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isEmpty() || optionalUser.get().getStatus() != UserStatus.UNACTIVATED) {
            return ResponseEntity.badRequest().body("User is already activated or does not exist");
        }

        User user = optionalUser.get();
        if (!twoFactorAuthService.isCodeValid(user.getMfaSecret(), request.getTwoFactorCode())) {
            return ResponseEntity.badRequest().body("Invalid 2FA code");
        }

        UserResponse updated = userService.forceChangePassword(username, request.getNewPassword());
        User latestUser = userService.getByUsername(updated.getUsername());
        latestUser.setStatus(UserStatus.ENABLED);
        latestUser.setMfaEnabled(true);
        latestUser.setMfaBoundAt(LocalDateTime.now());
        latestUser.setMustChangePassword(false);
        userRepository.save(latestUser);

        ClientMetadata metadata = clientMetadataResolver.resolve(servletRequest.getHeader("User-Agent"));
        LoginResponse response = issueSession(latestUser, servletRequest, metadata);
        loginLogRepository.save(LoginLog.builder()
                .userId(latestUser.getId())
                .username(latestUser.getUsername())
                .sessionId(response.getSessionId())
                .ipAddress(servletRequest.getRemoteAddr())
                .userAgent(servletRequest.getHeader("User-Agent"))
                .deviceInfo(metadata.deviceInfo())
                .browser(metadata.browser())
                .os(metadata.os())
                .status(LoginLog.LoginStatus.SUCCESS)
                .build());
        return ResponseEntity.ok(response);
    }

    private LoginResponse issueSession(User user, HttpServletRequest request, ClientMetadata metadata) {
        String sessionId = jwtUtil.newTokenId();
        String refreshTokenId = jwtUtil.newTokenId();
        String authorities = userService.getAuthorities(user.getUsername()).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        String accessToken = jwtUtil.generateAccessToken(user.getUsername(), authorities, sessionId, jwtUtil.newTokenId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername(), sessionId, refreshTokenId);
        User updatedUser = userService.updateLastLogin(user.getUsername(), request.getRemoteAddr());
        userSessionService.createSession(
                updatedUser,
                sessionId,
                refreshTokenId,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                metadata.deviceInfo(),
                LocalDateTime.now().plus(jwtUtil.getJwtConfig().getExpiration()),
                LocalDateTime.now().plus(jwtUtil.getJwtConfig().getRefreshExpiration()));

        LoginResponse response = new LoginResponse();
        response.setToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setUser(userMapper.toResponse(updatedUser));
        response.setMfaRequired(false);
        response.setExpiresIn(jwtUtil.getJwtConfig().getExpiration().toSeconds());
        response.setSessionId(sessionId);
        response.setNextAction("NONE");
        return response;
    }

    private boolean isValidSetupToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            return jwtUtil.validateToken(token)
                    && jwtUtil.isTokenNotExpired(token)
                    && "setup".equals(jwtUtil.getTokenType(token));
        } catch (Exception e) {
            log.warn("Invalid setup token: {}", e.getMessage());
            return false;
        }
    }
}
