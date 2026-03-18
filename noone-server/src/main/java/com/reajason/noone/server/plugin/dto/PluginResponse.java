package com.reajason.noone.server.plugin.dto;

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
    private String source;
    private String description;
    private String author;
    private Map<String, Object> actions;
    private Map<String, Object> meta;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
