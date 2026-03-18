package com.reajason.noone.server.plugin.dto;

import lombok.Data;

@Data
public class PluginUpdateRequest {
    private String name;
    private String description;
    private String author;
    private String type;
    private String runMode;
}
