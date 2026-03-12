package com.reajason.noone.server.shell.dto;

import com.reajason.noone.server.shell.ShellLanguage;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for shell connection
 *
 * @author ReaJason
 * @since 2025/12/27
 */
@Data
public class ShellResponse {
    private Long id;
    private String name;
    private String url;
    private ShellLanguage language;
    private String shellType;
    private String status;
    private Long projectId;
    private LocalDateTime createdAt;
    private LocalDateTime lastOnlineAt;
    private LocalDateTime updatedAt;

    // Profile related fields
    private Long profileId;
    private String profileName;

    // Connection configuration
    private String proxyUrl;
    private Map<String, String> customHeaders;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Boolean skipSslVerify;
    private Integer maxRetries;
    private Long retryDelayMs;

    // Normalized system info
    private Map<String, Object> basicInfo;
}
