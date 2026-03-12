package com.reajason.noone.server.admin.plugin;

import com.reajason.noone.server.admin.plugin.dto.PluginCreateRequest;
import com.reajason.noone.server.admin.plugin.dto.PluginQueryRequest;
import com.reajason.noone.server.admin.plugin.dto.PluginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class PluginService {

    private final PluginRepository pluginRepository;
    private final PluginMapper pluginMapper;

    public PluginResponse create(PluginCreateRequest request) {
        Optional<Plugin> existing = pluginRepository.findByPluginIdAndVersionAndLanguage(request.getId(), request.getVersion(), request.getLanguage());

        Plugin plugin;
        if (existing.isPresent()) {
            plugin = existing.get();
            plugin.setPluginId(request.getId());
            plugin.setName(request.getName());
            plugin.setVersion(request.getVersion());
            plugin.setLanguage(request.getLanguage());
            plugin.setType(request.getType());
            plugin.setRunMode(request.getRunMode());
            plugin.setPayload(request.getPayload());
            plugin.setActions(request.getActions());
        } else {
            plugin = pluginMapper.toEntity(request);
        }

        Plugin savedPlugin = pluginRepository.save(plugin);
        return pluginMapper.toResponse(savedPlugin);
    }

    @Transactional(readOnly = true)
    public Page<PluginResponse> query(PluginQueryRequest request) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortOrder())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                request.getSortBy()
        );

        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        Specification<Plugin> spec = PluginSpecifications.hasName(request.getName())
                .and(PluginSpecifications.hasLanguage(request.getLanguage()))
                .and(PluginSpecifications.hasType(request.getType()));

        return pluginRepository.findAll(spec, pageable).map(pluginMapper::toResponse);
    }
}
