package com.reajason.noone.server.admin.plugin;

import com.reajason.noone.server.admin.plugin.dto.PluginCreateRequest;
import com.reajason.noone.server.admin.plugin.dto.PluginResponse;
import org.springframework.stereotype.Component;

@Component
public class PluginMapper {

    public Plugin toEntity(PluginCreateRequest request) {
        Plugin plugin = new Plugin();
        plugin.setPluginId(request.getId());
        plugin.setName(request.getName());
        plugin.setVersion(request.getVersion());
        plugin.setLanguage(request.getLanguage());
        plugin.setType(request.getType());
        plugin.setRunMode(request.getRunMode());
        plugin.setPayload(request.getPayload());
        plugin.setActions(request.getActions());
        return plugin;
    }

    public PluginResponse toResponse(Plugin plugin) {
        PluginResponse response = new PluginResponse();
        response.setId(plugin.getPluginId());
        response.setName(plugin.getName());
        response.setVersion(plugin.getVersion());
        response.setLanguage(plugin.getLanguage());
        response.setType(plugin.getType());
        response.setRunMode(plugin.getRunMode());
        response.setActions(plugin.getActions());
        response.setCreatedAt(plugin.getCreatedAt());
        response.setUpdatedAt(plugin.getUpdatedAt());
        return response;
    }
}
