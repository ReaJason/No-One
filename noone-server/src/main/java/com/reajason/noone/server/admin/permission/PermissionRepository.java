package com.reajason.noone.server.admin.permission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PermissionRepository extends JpaRepository<Permission, Long>, JpaSpecificationExecutor<Permission> {
    
    boolean existsByCode(String code);
    
    boolean existsByCodeAndIdNot(String code, Long id);
}