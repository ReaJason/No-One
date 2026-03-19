package com.reajason.noone.server.config;

import com.reajason.noone.server.admin.user.UserService;
import com.reajason.noone.server.admin.user.UserSessionService;
import com.reajason.noone.server.util.IpUtils;
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
    private final UserSessionService userSessionService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, JwtConfig jwtConfig, UserService userService,
            UserSessionService userSessionService) {
        this.jwtUtil = jwtUtil;
        this.jwtConfig = jwtConfig;
        this.userService = userService;
        this.userSessionService = userSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(jwtConfig.getHeader());
        String token = jwtUtil.getTokenFromHeader(authHeader);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtUtil.validateToken(token)
                    && jwtUtil.isTokenNotExpired(token)
                    && "access".equals(jwtUtil.getTokenType(token))) {
                try {
                    String sessionId = jwtUtil.getSessionId(token);
                    String username = jwtUtil.getUsernameFromToken(token);
                    boolean validSession = sessionId != null && userSessionService.isSessionValid(sessionId);
                    if (username != null && validSession) {
                        Set<GrantedAuthority> authorities = userService.getAuthorities(username);
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                username, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        userSessionService.touchSession(
                                sessionId,
                                IpUtils.getIpAddr(request),
                                request.getHeader("User-Agent"));
                    }
                } catch (Exception e) {
                    log.error("JWT authentication failed: {}", e.getMessage());
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
