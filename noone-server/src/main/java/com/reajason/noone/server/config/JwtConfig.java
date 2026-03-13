package com.reajason.noone.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration expiration = Duration.ofSeconds(30);

    @DurationUnit(ChronoUnit.SECONDS)
    private Duration refreshExpiration = Duration.ofSeconds(604800);

    @DurationUnit(ChronoUnit.SECONDS)
    private Duration setupExpiration = Duration.ofSeconds(900);

    @DurationUnit(ChronoUnit.SECONDS)
    private Duration passwordChangeExpiration = Duration.ofSeconds(900);

    @DurationUnit(ChronoUnit.SECONDS)
    private Duration challengeExpiration = Duration.ofSeconds(300);

    private String header = "Authorization";
    private String prefix = "Bearer ";
    private String secret;
}
