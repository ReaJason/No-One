package com.reajason.noone.server.admin.permission;

import com.reajason.noone.NooneApplication;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NooneApplication.class)
@Import(TestPGContainerConfiguration.class)
@ActiveProfiles("test")
@Transactional
class PermissionSpecificationsTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        permissionRepository.deleteAll();
        roleRepository.deleteAll();
    }

    @Test
    void hasRole_ShouldReturnPermissionsWithSpecifiedRole() {
        // Given
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        Role savedAdminRole = roleRepository.save(adminRole);

        Role userRole = new Role();
        userRole.setName("USER");
        Role savedUserRole = roleRepository.save(userRole);

        Permission permission1 = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .roles(Set.of(savedAdminRole))
                .build();
        Permission permission2 = Permission.builder()
                .code("user:update")
                .name("更新用户")
                .roles(Set.of(savedUserRole))
                .build();
        Permission permission3 = Permission.builder()
                .code("role:create")
                .name("创建角色")
                .roles(Set.of(savedAdminRole, savedUserRole))
                .build();

        permissionRepository.save(permission1);
        permissionRepository.save(permission2);
        permissionRepository.save(permission3);

        // When
        Specification<Permission> spec = PermissionSpecifications.hasRole(savedAdminRole.getId());
        List<Permission> result = permissionRepository.findAll(spec);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Permission::getCode)
                .containsExactlyInAnyOrder("user:create", "role:create");
    }

    @Test
    void hasRole_WithNullRoleId_ShouldReturnAllPermissions() {
        // Given
        Permission permission1 = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission permission2 = Permission.builder()
                .code("user:update")
                .name("更新用户")
                .build();

        permissionRepository.save(permission1);
        permissionRepository.save(permission2);

        // When
        Specification<Permission> spec = PermissionSpecifications.hasRole(null);
        List<Permission> result = permissionRepository.findAll(spec);

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    void hasRole_WithNonExistentRoleId_ShouldReturnEmptyList() {
        // Given
        Permission permission1 = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        permissionRepository.save(permission1);

        // When
        Specification<Permission> spec = PermissionSpecifications.hasRole(999L);
        List<Permission> result = permissionRepository.findAll(spec);

        // Then
        assertThat(result).isEmpty();
    }
}






