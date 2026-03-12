package com.reajason.noone.server.admin.plugin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
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

    private String runMode;

    private String payload;

    private Map<String, Object> actions;
}
