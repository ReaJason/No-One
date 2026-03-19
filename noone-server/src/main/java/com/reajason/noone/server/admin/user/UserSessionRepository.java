package com.reajason.noone.server.admin.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long>, JpaSpecificationExecutor<UserSession> {
    Optional<UserSession> findBySessionId(String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from UserSession session where session.sessionId = :sessionId")
    Optional<UserSession> findBySessionIdForUpdate(@Param("sessionId") String sessionId);

    Optional<UserSession> findByUserIdAndSessionId(Long userId, String sessionId);

    List<UserSession> findByUserIdOrderByCreatedAtDesc(Long userId);
}
