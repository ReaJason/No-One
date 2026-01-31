package com.reajason.noone.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    private int expiration = 86400;
    private String header = "Authorization";
    private String prefix = "Bearer ";
}
