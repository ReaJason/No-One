package com.reajason.noone.server.plugin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PluginRepository extends JpaRepository<Plugin, Long>, JpaSpecificationExecutor<Plugin> {
    Optional<Plugin> findByPluginIdAndLanguage(String pluginId, String language);

    List<Plugin> findAllByLanguage(String language);
}
