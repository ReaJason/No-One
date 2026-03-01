package com.reajason.noone.server.shell.oplog.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ShellOperationLogResponse {
    private Long id;
    private Long shellId;
    private String username;
    private String operation;
    private String pluginId;
    private String action;
    private Map<String, Object> args;
    private Map<String, Object> result;
    private boolean success;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime createdAt;
}
