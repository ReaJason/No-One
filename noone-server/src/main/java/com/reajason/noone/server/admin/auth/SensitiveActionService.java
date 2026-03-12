package com.reajason.noone.server.admin.auth;

import com.reajason.noone.server.admin.auth.dto.SensitiveActionChallengeRequest;
import com.reajason.noone.server.admin.auth.dto.SensitiveActionChallengeResponse;
import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.config.AuthorizationService;
import com.reajason.noone.server.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class SensitiveActionService {

    public static final String CHALLENGE_HEADER = "X-Action-Challenge";

    private final AuthorizationService authorizationService;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorAuthService twoFactorAuthService;
    private final SensitiveActionChallengeRepository repository;
    private final JwtUtil jwtUtil;

    public SensitiveActionChallengeResponse createChallenge(SensitiveActionChallengeRequest request) {
        User user = authorizationService.getCurrentUser();
        String method = request.getVerificationMethod().trim().toUpperCase();
        if ("PASSWORD".equals(method)) {
            if (request.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Password verification failed");
            }
        } else if ("TOTP".equals(method)) {
            if (!user.isMfaEnabled() || request.getTwoFactorCode() == null
                    || !twoFactorAuthService.isCodeValid(user.getMfaSecret(), request.getTwoFactorCode())) {
                throw new IllegalArgumentException("Two-factor verification failed");
            }
        } else {
            throw new IllegalArgumentException("Unsupported verification method: " + request.getVerificationMethod());
        }

        String tokenId = jwtUtil.newTokenId();
        LocalDateTime expiresAt = LocalDateTime.now().plus(jwtUtil.getJwtConfig().getChallengeExpiration());

        SensitiveActionChallenge challenge = new SensitiveActionChallenge();
        challenge.setUser(user);
        challenge.setTokenId(tokenId);
        challenge.setVerificationMethod(method);
        challenge.setActionName(request.getAction());
        challenge.setTargetType(request.getTargetType());
        challenge.setTargetId(request.getTargetId());
        challenge.setExpiresAt(expiresAt);
        repository.save(challenge);

        String token = jwtUtil.generateActionToken(
                user.getUsername(),
                tokenId,
                "challenge",
                request.getAction(),
                request.getTargetType(),
                request.getTargetId(),
                jwtUtil.getJwtConfig().getChallengeExpiration());
        return new SensitiveActionChallengeResponse(token, expiresAt);
    }

    public void requireChallenge(String challengeToken, String actionName, String targetType, String targetId) {
        if (challengeToken == null || challengeToken.isBlank()) {
            throw new IllegalArgumentException("Sensitive action challenge is required");
        }
        if (!jwtUtil.validateToken(challengeToken) || !jwtUtil.isTokenNotExpired(challengeToken)) {
            throw new IllegalArgumentException("Sensitive action challenge is invalid or expired");
        }
        if (!"challenge".equals(jwtUtil.getTokenType(challengeToken))) {
            throw new IllegalArgumentException("Sensitive action challenge type is invalid");
        }

        String username = jwtUtil.getUsernameFromToken(challengeToken);
        User user = authorizationService.getCurrentUser();
        if (!user.getUsername().equals(username)) {
            throw new IllegalArgumentException("Sensitive action challenge does not belong to current user");
        }

        String tokenId = jwtUtil.getTokenId(challengeToken);
        SensitiveActionChallenge challenge = repository.findByTokenId(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Sensitive action challenge was not found"));

        if (challenge.getUsedAt() != null) {
            throw new IllegalArgumentException("Sensitive action challenge has already been used");
        }
        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Sensitive action challenge has expired");
        }
        if (!challenge.getActionName().equals(actionName)) {
            throw new IllegalArgumentException("Sensitive action challenge does not match action");
        }
        if (!matches(challenge.getTargetType(), targetType) || !matches(challenge.getTargetId(), targetId)) {
            throw new IllegalArgumentException("Sensitive action challenge does not match target");
        }

        challenge.setUsedAt(LocalDateTime.now());
        repository.save(challenge);
    }

    private boolean matches(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null || right.isBlank();
        }
        return left.equals(right);
    }
}
