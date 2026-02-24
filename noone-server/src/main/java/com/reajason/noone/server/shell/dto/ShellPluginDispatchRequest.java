package com.reajason.noone.server.shell.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ShellPluginDispatchRequest {
    private String pluginId;
    private String action;
    private Map<String, Object> args;
}
