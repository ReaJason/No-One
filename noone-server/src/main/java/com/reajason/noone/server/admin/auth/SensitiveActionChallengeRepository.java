package com.reajason.noone.server.admin.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SensitiveActionChallengeRepository extends JpaRepository<SensitiveActionChallenge, Long> {
    Optional<SensitiveActionChallenge> findByTokenId(String tokenId);
}
