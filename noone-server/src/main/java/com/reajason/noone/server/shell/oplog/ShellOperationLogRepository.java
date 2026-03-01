package com.reajason.noone.server.shell.oplog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ShellOperationLogRepository extends JpaRepository<ShellOperationLog, Long>, JpaSpecificationExecutor<ShellOperationLog> {

    Optional<ShellOperationLog> findFirstByShellIdAndUsernameAndPluginIdAndSuccessTrueOrderByCreatedAtDesc(
            Long shellId, String username, String pluginId);
}
