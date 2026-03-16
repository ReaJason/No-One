package com.reajason.noone.server.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuditRequestContextFilter extends OncePerRequestFilter {

    private final RequestContext requestContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        requestContext.setIpAddress(request.getRemoteAddr());
        requestContext.setUserAgent(request.getHeader("User-Agent"));
        requestContext.setRequestMethod(request.getMethod());
        requestContext.setRequestUri(request.getRequestURI());
        filterChain.doFilter(request, response);
    }
}
