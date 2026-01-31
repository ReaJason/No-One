package com.reajason.noone.server.shell.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * Request DTO for testing shell configuration without saving
 *
 * @author ReaJason
 * @since 2025/1/22
 */
@Data
public class ShellTestConfigRequest {
    @NotBlank(message = "URL cannot be blank")
    private String url;

    @NotNull(message = "Profile ID cannot be null")
    private Long profileId;

    private String proxyUrl;

    private Map<String, String> customHeaders;

    private Integer connectTimeoutMs;

    private Integer readTimeoutMs;

    private Boolean skipSslVerify;

    private Integer maxRetries;

    private Long retryDelayMs;
}
