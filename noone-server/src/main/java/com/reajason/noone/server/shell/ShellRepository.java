package com.reajason.noone.server.shell;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Shell repository interface
 *
 * @author ReaJason
 * @since 2025/12/27
 */
public interface ShellRepository extends JpaRepository<Shell, Long>, JpaSpecificationExecutor<Shell> {

}
