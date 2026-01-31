package com.reajason.noone.server.shell.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * Request DTO for creating a new shell connection
 *
 * @author ReaJason
 * @since 2025/12/27
 */
@Data
public class ShellCreateRequest {
    @NotBlank(message = "URL cannot be blank")
    private String url;

    private String group;

    private Long projectId;

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
