package com.reajason.noone.server.plugin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PluginInstallRequest {
    @NotBlank
    private String pluginId;
    @NotBlank
    private String language;
}
