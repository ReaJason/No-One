package com.reajason.noone.server.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ProfileRepository extends JpaRepository<Profile, Long>, JpaSpecificationExecutor<Profile> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);

    Profile getByNameEquals(String name);
}

