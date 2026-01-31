package com.reajason.noone.server.shell.dto;

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
    private String url;
    private String shellType;
    private String status;
    private String group;
    private Long projectId;
    private LocalDateTime createTime;
    private LocalDateTime connectTime;
    private LocalDateTime updateTime;

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
}
