package com.reajason.noone.server.config;

import com.reajason.noone.server.admin.user.UserService;
import com.reajason.noone.server.admin.user.UserSessionService;
import com.reajason.noone.server.util.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private UserService userService;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateAndTouchSessionWhenTokenAndSessionAreValid() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, jwtConfig, userService, userSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        request.addHeader("User-Agent", "JUnit");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtConfig.getHeader()).thenReturn("Authorization");
        when(jwtUtil.getTokenFromHeader("Bearer access-token")).thenReturn("access-token");
        when(jwtUtil.validateToken("access-token")).thenReturn(true);
        when(jwtUtil.isTokenNotExpired("access-token")).thenReturn(true);
        when(jwtUtil.getTokenType("access-token")).thenReturn("access");
        when(jwtUtil.getSessionId("access-token")).thenReturn("session-1");
        when(jwtUtil.getUsernameFromToken("access-token")).thenReturn("alice");
        when(userSessionService.isSessionValid("session-1")).thenReturn(true);
        when(userService.getAuthorities("alice")).thenReturn(Set.copyOf(AuthorityUtils.createAuthorityList("user:read")));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
        verify(userSessionService).touchSession(eq("session-1"), anyString(), eq("JUnit"));
    }

    @Test
    void shouldSkipAuthenticationWhenSessionIsInvalid() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, jwtConfig, userService, userSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtConfig.getHeader()).thenReturn("Authorization");
        when(jwtUtil.getTokenFromHeader("Bearer access-token")).thenReturn("access-token");
        when(jwtUtil.validateToken("access-token")).thenReturn(true);
        when(jwtUtil.isTokenNotExpired("access-token")).thenReturn(true);
        when(jwtUtil.getTokenType("access-token")).thenReturn("access");
        when(jwtUtil.getSessionId("access-token")).thenReturn("session-1");
        when(jwtUtil.getUsernameFromToken("access-token")).thenReturn("alice");
        when(userSessionService.isSessionValid("session-1")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userService, never()).getAuthorities(anyString());
        verify(userSessionService, never()).touchSession(anyString(), any(), any());
    }
}
