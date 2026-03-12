package com.reajason.noone.server.admin.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserIpWhitelistRepository extends JpaRepository<UserIpWhitelist, Long> {
    List<UserIpWhitelist> findByUserId(Long userId);

    boolean existsByUserIdAndIpAddress(Long userId, String ipAddress);

    boolean existsByUserId(Long userId);
}
