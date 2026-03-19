package com.reajason.noone.server.admin.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserIpWhitelistRepository extends JpaRepository<UserIpWhitelist, Long> {
    List<UserIpWhitelist> findByUserIdOrderByCreatedAtAsc(Long userId);

    boolean existsByUserIdAndIpAddress(Long userId, String ipAddress);

    boolean existsByUserId(Long userId);

    Optional<UserIpWhitelist> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
