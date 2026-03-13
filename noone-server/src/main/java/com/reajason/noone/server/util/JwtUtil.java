package com.reajason.noone.server.util;

import com.reajason.noone.server.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Slf4j
@Component
public class JwtUtil {

    @Resource
    private JwtConfig jwtConfig;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        if (StringUtils.hasText(jwtConfig.getSecret())) {
            byte[] keyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
            signingKey = Keys.hmacShaKeyFor(keyBytes);
        } else {
            signingKey = Jwts.SIG.HS512.key().build();
            log.warn("JWT secret is not configured in jwt.secret, using a randomly generated key. Issued tokens will be invalidated upon server restart.");
        }
    }

    public String generateToken(Authentication authentication) {
        return generateAccessToken(
                authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(",")),
                newTokenId(),
                newTokenId());
    }

    public String generateAccessToken(String username, String authorities, String sessionId, String tokenId) {
        return buildToken(username, tokenId, jwtConfig.getExpiration(), claims(
                "authorities", authorities,
                "sessionId", sessionId,
                "tokenType", "access"));
    }

    public String generateRefreshToken(String username, String sessionId, String tokenId) {
        return buildToken(username, tokenId, jwtConfig.getRefreshExpiration(), claims(
                "sessionId", sessionId,
                "tokenType", "refresh"));
    }

    public String generateSetupToken(String username) {
        return buildToken(username, newTokenId(), jwtConfig.getSetupExpiration(), claims(
                "authorities", "ROLE_SETUP",
                "tokenType", "setup"));
    }

    public String generatePasswordChangeToken(String username) {
        return buildToken(username, newTokenId(), jwtConfig.getPasswordChangeExpiration(), claims(
                "tokenType", "password_change"));
    }

    public String generateActionToken(
            String username,
            String tokenId,
            String tokenType,
            String action,
            String targetType,
            String targetId,
            Duration expiration) {
        Map<String, Object> claims = claims("tokenType", tokenType);
        if (action != null) {
            claims.put("action", action);
        }
        if (targetType != null) {
            claims.put("targetType", targetType);
        }
        if (targetId != null) {
            claims.put("targetId", targetId);
        }
        return buildToken(username, tokenId, expiration, claims);
    }

    public String getSessionId(String token) {
        return parseClaims(token).get("sessionId", String.class);
    }

    public String getTokenType(String token) {
        return parseClaims(token).get("tokenType", String.class);
    }

    public String getTokenId(String token) {
        return parseClaims(token).getId();
    }

    public String getAction(String token) {
        return parseClaims(token).get("action", String.class);
    }

    public String getTargetType(String token) {
        return parseClaims(token).get("targetType", String.class);
    }

    public String getTargetId(String token) {
        return parseClaims(token).get("targetId", String.class);
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getAuthoritiesFromToken(String token) {
        return parseClaims(token).get("authorities", String.class);
    }

    public Instant getExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenNotExpired(String token) {
        try {
            return !parseClaims(token).getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT token expiration check failed: {}", e.getMessage());
            return false;
        }
    }

    public String getTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith(jwtConfig.getPrefix())) {
            return authHeader.substring(jwtConfig.getPrefix().length());
        }
        return null;
    }

    public String getTokenSignature(String token) {
        String[] parts = token.split("\\.");
        if (parts.length == 3) {
            return parts[2];
        }
        return token;
    }

    public String newTokenId() {
        return UUID.randomUUID().toString();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String buildToken(String username, String tokenId, Duration expiration, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .id(tokenId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(getSigningKey())
                .compact();
    }

    private Map<String, Object> claims(Object... kvPairs) {
        Map<String, Object> claims = new HashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            claims.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
        }
        return claims;
    }
}
