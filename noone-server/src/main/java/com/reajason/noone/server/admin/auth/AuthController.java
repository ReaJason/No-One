package com.reajason.noone.server.admin.auth;

import com.reajason.noone.server.admin.auth.dto.LoginRequest;
import com.reajason.noone.server.admin.auth.dto.LoginResponse;
import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserMapper;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
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

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );
            String token = jwtUtil.generateToken(authentication);
            log.info("User {} logged in successfully", loginRequest.getUsername());
            Optional<User> user = userRepository.findByUsername(loginRequest.getUsername());
            return ResponseEntity.ok(new LoginResponse(token, userMapper.toResponse(user.get())));
        } catch (AuthenticationException e) {
            log.warn("Login failed for user {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = jwtUtil.getTokenFromHeader(authHeader);
            if (token != null && jwtUtil.validateToken(token) && jwtUtil.isTokenNotExpired(token)) {
                String username = jwtUtil.getUsernameFromToken(token);
                String authorities = jwtUtil.getAuthoritiesFromToken(token);
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        username, null,
                        authorities != null ?
                                Arrays.stream(authorities.split(","))
                                        .map(String::trim)
                                        .map(SimpleGrantedAuthority::new)
                                        .collect(Collectors.toList()) :
                                Collections.emptyList()
                );

                String newToken = jwtUtil.generateToken(authentication);
                log.info("Token refreshed for user {}", username);

                return ResponseEntity.ok(new LoginResponse(newToken, null));
            } else {
                return ResponseEntity.badRequest()
                        .body(new LoginResponse(null, null));
            }
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new LoginResponse(null, null));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(new LoginResponse(null, null));
    }
}
