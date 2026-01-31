package com.reajason.noone.server.config;

import com.reajason.noone.server.admin.user.UserService;
import com.reajason.noone.server.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtConfig jwtConfig;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, JwtConfig jwtConfig, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.jwtConfig = jwtConfig;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(jwtConfig.getHeader());
        String token = jwtUtil.getTokenFromHeader(authHeader);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtUtil.validateToken(token) && jwtUtil.isTokenNotExpired(token)) {
                try {
                    String username = jwtUtil.getUsernameFromToken(token);
                    if (username != null) {
                        Set<GrantedAuthority> authorities = userService.getAuthorities(username);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(username, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception e) {
                    log.error("JWT authentication failed: {}", e.getMessage());
                    SecurityContextHolder.clearContext();
                }
            } else {
                log.debug("JWT token is invalid or expired");
            }
        }
        filterChain.doFilter(request, response);
    }
}
