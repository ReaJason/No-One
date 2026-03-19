package com.reajason.noone.server.admin.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginLogRepository extends JpaRepository<LoginLog, Long>, JpaSpecificationExecutor<LoginLog> {
}
