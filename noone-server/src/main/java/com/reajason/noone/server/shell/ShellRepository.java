package com.reajason.noone.server.shell;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * Shell repository interface
 *
 * @author ReaJason
 * @since 2025/12/27
 */
public interface ShellRepository extends JpaRepository<Shell, Long>, JpaSpecificationExecutor<Shell> {
    /**
     * Find shells by project ID
     */
    List<Shell> findByProjectId(Long projectId);

    /**
     * Find shells by group
     */
    List<Shell> findByGroup(String group);

    /**
     * Find shells by status
     */
    List<Shell> findByStatus(ShellStatus status);

    /**
     * Check if shell exists by URL
     */
    boolean existsByUrl(String url);
}
