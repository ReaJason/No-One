package com.reajason.noone.server.config;

import com.reajason.noone.server.admin.user.UserIpWhitelistRepository;
import com.reajason.noone.server.admin.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpWhitelistFilter extends OncePerRequestFilter {

    private final UserIpWhitelistRepository ipWhitelistRepository;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if ("/api/auth/login".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod())) {
            // Need to read username. Since it's in the body, it's tricky here.
            // Alternative: check IP during CustomAuthenticationProvider, right after
            // loading user.
            // Let's implement it inside CustomAuthenticationProvider for easier access to
            // the User object and RequestContextHolder
            log.trace("IP whitelist check deferred to Authentication Provider or Controller");
        }

        filterChain.doFilter(request, response);
    }
}
