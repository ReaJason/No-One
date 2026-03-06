package com.reajason.noone.server.admin.plugin.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class PluginResponse {
    private String id;
    private String name;
    private String version;
    private String language;
    private String type;
    private String runMode;
    private Map<String, Object> actions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
