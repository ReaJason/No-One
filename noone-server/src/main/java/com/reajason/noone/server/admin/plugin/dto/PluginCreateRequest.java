package com.reajason.noone.server.admin.plugin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class PluginCreateRequest {
    @NotBlank
    private String id;

    @NotBlank
    private String name;

    @NotBlank
    private String version;

    @NotBlank
    private String language;

    @NotBlank
    private String type;

    private String payload;

    private Map<String, Object> actions;
}
