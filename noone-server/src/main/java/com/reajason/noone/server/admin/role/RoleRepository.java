package com.reajason.noone.server.admin.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * @author ReaJason
 * @since 2025/9/9
 */
public interface RoleRepository extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {
    
    boolean existsByName(String name);
    
    boolean existsByNameAndIdNot(String name, Long id);
}
