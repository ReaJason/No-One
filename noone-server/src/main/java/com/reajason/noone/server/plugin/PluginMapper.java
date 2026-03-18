package com.reajason.noone.server.plugin;

import com.reajason.noone.server.plugin.dto.PluginCreateRequest;
import com.reajason.noone.server.plugin.dto.PluginResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PluginMapper {

    @Mapping(target = "pluginId", source = "id")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "source", ignore = true)
    Plugin toEntity(PluginCreateRequest request);

    @Mapping(target = "id", source = "pluginId")
    @Mapping(target = "source", expression = "java(plugin.getSource() != null ? plugin.getSource().name() : null)")
    PluginResponse toResponse(Plugin plugin);
}
