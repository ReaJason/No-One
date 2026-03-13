package com.reajason.noone.server.shell.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShellPluginStatusResponse {
    private String pluginId;
    private String serverVersion;
    private String shellVersion;
    private boolean loaded;
    private boolean needsUpdate;
}
