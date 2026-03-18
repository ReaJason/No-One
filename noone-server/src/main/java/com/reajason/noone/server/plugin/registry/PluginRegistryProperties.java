package com.reajason.noone.server.plugin.registry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "noone.plugin-registry")
public class PluginRegistryProperties {
    private boolean enabled = false;
    private String catalogUrl = "";
}
