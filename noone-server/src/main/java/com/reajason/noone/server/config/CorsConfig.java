package com.reajason.noone.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.Collections;

/**
 * CORS (Cross-Origin Resource Sharing) configuration
 * Allows cross-origin requests from the frontend application
 *
 * @author ReaJason
 * @since 2025/12/27
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow credentials (cookies, authorization headers)
        config.setAllowCredentials(true);

        // Allow origins - configure based on environment
        // In development, allow localhost with any port
        config.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://[::1]:*"));

        // For production, you should specify exact origins:
        // config.setAllowedOrigins(Arrays.asList("https://yourdomain.com"));

        // Allow all HTTP methods
        config.setAllowedMethods(Arrays.asList(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS",
                "PATCH"));

        // Allow all headers
        config.setAllowedHeaders(Collections.singletonList("*"));

        // Expose headers that the client can access
        config.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Request-ID"));

        // How long the response from a pre-flight request can be cached (in seconds)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply CORS configuration to all endpoints
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
