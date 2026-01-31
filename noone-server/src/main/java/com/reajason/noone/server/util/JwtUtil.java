package com.reajason.noone.server.util;

import com.reajason.noone.server.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtUtil {

    @Resource
    private JwtConfig jwtConfig;

    // 随机生成 JWT 加密 key，舒服
    // 但是重启完，之前分发 JWT 都会失效，会触发重新登录，因为 key 变了解密不了了
    @Getter
    private final SecretKey signingKey = Jwts.SIG.HS512.key().build();

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(username)
                .claim("authorities", authorities)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration() * 1000L))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token).getPayload()
                .getSubject();
    }

    public String getAuthoritiesFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token).getPayload()
                .get("authorities", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenNotExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token).getPayload();
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT token expiration check failed: {}", e.getMessage());
            return false;
        }
    }

    public String getTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith(jwtConfig.getPrefix())) {
            return authHeader.substring(jwtConfig.getPrefix().length());
        }
        return null;
    }
}
