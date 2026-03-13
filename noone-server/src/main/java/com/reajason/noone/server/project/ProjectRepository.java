package com.reajason.noone.server.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {
    boolean existsByNameAndDeletedFalse(String name);
    boolean existsByNameAndIdNotAndDeletedFalse(String name, Long id);
    boolean existsByCodeAndDeletedFalse(String code);
    boolean existsByCodeAndIdNotAndDeletedFalse(String code, Long id);
    Optional<Project> findByIdAndDeletedFalse(Long id);
}
