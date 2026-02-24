package com.reajason.noone.server.admin.plugin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PluginRepository extends JpaRepository<Plugin, String>, JpaSpecificationExecutor<Plugin> {
    Optional<Plugin> findByPluginIdAndVersionAndLanguage(String pluginId, String version, String language);

    Optional<Plugin> findByPluginIdAndLanguage(String pluginId, String language);
}
