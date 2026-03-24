package com.reajason.noone.server.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProfileRepository extends JpaRepository<ProfileEntity, Long>, JpaSpecificationExecutor<ProfileEntity> {
    boolean existsByNameAndDeletedFalse(String name);
    boolean existsByNameAndIdNotAndDeletedFalse(String name, Long id);
    Optional<ProfileEntity> findByIdAndDeletedFalse(Long id);
}
