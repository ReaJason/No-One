package com.reajason.noone.server.service;

import com.reajason.noone.NooneApplication;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.admin.permission.PermissionService;
import com.reajason.noone.server.admin.permission.dto.PermissionCreateRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionQueryRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionResponse;
import com.reajason.noone.server.admin.permission.dto.PermissionUpdateRequest;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = NooneApplication.class)
@Import(TestPGContainerConfiguration.class)
@ActiveProfiles("test")
@Transactional
class PermissionServiceTest {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Role adminRole;
    private Role userRole;

    @BeforeEach
    void setUp() {
        // Clean up existing data
        permissionRepository.deleteAll();
        roleRepository.deleteAll();

        // Create test roles
        adminRole = new Role();
        adminRole.setName("ADMIN");
        adminRole = roleRepository.save(adminRole);

        userRole = new Role();
        userRole.setName("USER");
        userRole = roleRepository.save(userRole);
    }

    @Test
    void createPermission_ShouldCreatePermissionSuccessfully() {
        // Given
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode("user:create");
        request.setName("创建用户");
        request.setRoleIds(Set.of());

        // When
        PermissionResponse response = permissionService.createPermission(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("user:create");
        assertThat(response.getName()).isEqualTo("创建用户");
        assertThat(response.getCategory()).isEqualTo("user");
        assertThat(response.getId()).isNotNull();

        // Verify permission is saved in database
        Permission savedPermission = permissionRepository.findById(response.getId()).orElse(null);
        assertThat(savedPermission).isNotNull();
        assertThat(savedPermission.getCode()).isEqualTo("user:create");
        assertThat(savedPermission.getName()).isEqualTo("创建用户");
    }

    @Test
    void createPermission_WithRoles_ShouldCreatePermissionWithRoles() {
        // Given
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode("user:create");
        request.setName("创建用户");
        request.setRoleIds(Set.of(adminRole.getId(), userRole.getId()));

        // When
        PermissionResponse response = permissionService.createPermission(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).hasSize(2);
    }

    @Test
    void createPermission_WithDuplicateCode_ShouldThrowException() {
        // Given
        PermissionCreateRequest request1 = new PermissionCreateRequest();
        request1.setCode("user:create");
        request1.setName("创建用户");
        permissionService.createPermission(request1);

        PermissionCreateRequest request2 = new PermissionCreateRequest();
        request2.setCode("user:create");
        request2.setName("创建用户权限");

        // When & Then
        assertThatThrownBy(() -> permissionService.createPermission(request2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限代码已存在：user:create");
    }

    @Test
    void createPermission_WithEmptyRoleIds_ShouldCreatePermissionWithoutRoles() {
        // Given
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode("user:create");
        request.setName("创建用户");
        request.setRoleIds(Set.of()); // Empty set

        // When
        PermissionResponse response = permissionService.createPermission(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("user:create");
        assertThat(response.getRoles()).isEmpty();
    }

    @Test
    void createPermission_WithNullRoleIds_ShouldCreatePermissionWithoutRoles() {
        // Given
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode("user:create");
        request.setName("创建用户");
        request.setRoleIds(null); // Null role IDs

        // When
        PermissionResponse response = permissionService.createPermission(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("user:create");
        assertThat(response.getRoles()).isEmpty();
    }

    @Test
    void getPermissionById_ShouldReturnPermissionSuccessfully() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        // When
        PermissionResponse response = permissionService.getPermissionById(savedPermission.getId());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(savedPermission.getId());
        assertThat(response.getCode()).isEqualTo("user:create");
        assertThat(response.getName()).isEqualTo("创建用户");
        assertThat(response.getCategory()).isEqualTo("user");
    }

    @Test
    void getPermissionById_WithNonExistentId_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> permissionService.getPermissionById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限不存在：999");
    }

    @Test
    void updatePermission_ShouldUpdatePermissionSuccessfully() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setCode("user:update");
        request.setName("更新用户");
        request.setRoleIds(Set.of(adminRole.getId()));

        // When
        PermissionResponse response = permissionService.updatePermission(savedPermission.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("user:update");
        assertThat(response.getName()).isEqualTo("更新用户");
        assertThat(response.getCategory()).isEqualTo("user");
        assertThat(response.getRoles()).hasSize(1);
        assertThat(response.getRoles().iterator().next().getName()).isEqualTo("ADMIN");

        // Verify permission is updated in database
        Permission updatedPermission = permissionRepository.findById(savedPermission.getId()).orElse(null);
        assertThat(updatedPermission).isNotNull();
        assertThat(updatedPermission.getCode()).isEqualTo("user:update");
        assertThat(updatedPermission.getName()).isEqualTo("更新用户");
    }

    @Test
    void updatePermission_WithPartialUpdate_ShouldUpdateOnlySpecifiedFields() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setName("创建用户权限");
        // Don't set code, should remain unchanged

        // When
        PermissionResponse response = permissionService.updatePermission(savedPermission.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("user:create"); // Should remain unchanged
        assertThat(response.getName()).isEqualTo("创建用户权限"); // Should be updated

        // Verify in database
        Permission updatedPermission = permissionRepository.findById(savedPermission.getId()).orElse(null);
        assertThat(updatedPermission).isNotNull();
        assertThat(updatedPermission.getCode()).isEqualTo("user:create");
        assertThat(updatedPermission.getName()).isEqualTo("创建用户权限");
    }

    @Test
    void updatePermission_WithDuplicateCode_ShouldThrowException() {
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
        Permission savedPermission2 = permissionRepository.save(permission2);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setCode("user:create"); // Duplicate code
        request.setName("更新用户权限");

        // When & Then
        assertThatThrownBy(() -> permissionService.updatePermission(savedPermission2.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限代码已存在：user:create");
    }

    @Test
    void updatePermission_WithNonExistentId_ShouldThrowException() {
        // Given
        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setCode("user:update");
        request.setName("更新用户");

        // When & Then
        assertThatThrownBy(() -> permissionService.updatePermission(999L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限不存在：999");
    }

    @Test
    void updatePermission_WithNullRoleIds_ShouldNotUpdateRoles() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .roles(Set.of(adminRole))
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setName("创建用户权限");
        request.setRoleIds(null); // Null role IDs

        // When
        PermissionResponse response = permissionService.updatePermission(savedPermission.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("创建用户权限");
        assertThat(response.getRoles()).hasSize(1); // Should keep existing roles
        assertThat(response.getRoles().iterator().next().getName()).isEqualTo("ADMIN");
    }

    @Test
    void updatePermission_WithEmptyRoleIds_ShouldClearRoles() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .roles(Set.of(adminRole))
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setName("创建用户权限");
        request.setRoleIds(Set.of()); // Empty role IDs

        // When
        PermissionResponse response = permissionService.updatePermission(savedPermission.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("创建用户权限");
        assertThat(response.getRoles()).isEmpty(); // Should clear roles
    }

    @Test
    void deletePermission_ShouldDeletePermissionSuccessfully() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        // When
        permissionService.deletePermission(savedPermission.getId());

        // Then
        assertThat(permissionRepository.existsById(savedPermission.getId())).isFalse();
    }

    @Test
    void deletePermission_WithNonExistentId_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> permissionService.deletePermission(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限不存在：999");
    }

    @Test
    void queryPermissions_ShouldReturnPaginatedResults() {
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

        PermissionQueryRequest request = new PermissionQueryRequest();
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<PermissionResponse> result = permissionService.queryPermissions(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void queryPermissions_WithCategoryFilter_ShouldReturnFilteredResults() {
        // Given
        Permission userPermission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission rolePermission = Permission.builder()
                .code("role:create")
                .name("创建角色")
                .build();
        permissionRepository.save(userPermission);
        permissionRepository.save(rolePermission);

        PermissionQueryRequest request = new PermissionQueryRequest();
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<PermissionResponse> result = permissionService.queryPermissions(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCode()).isEqualTo("user:create");
        assertThat(result.getContent().get(0).getCategory()).isEqualTo("user");
    }

    @Test
    void queryPermissions_WithRoleFilter_ShouldReturnFilteredResults() {
        // Given
        Permission permission1 = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .roles(Set.of(adminRole))
                .build();
        Permission permission2 = Permission.builder()
                .code("user:update")
                .name("更新用户")
                .roles(Set.of(userRole))
                .build();
        permissionRepository.save(permission1);
        permissionRepository.save(permission2);

        PermissionQueryRequest request = new PermissionQueryRequest();
        request.setRoleId(adminRole.getId());
        request.setPage(0);
        request.setPageSize(10);

        List<Permission> permissions = permissionRepository.findAll();
        assertThat(permissions).hasSize(2);
        // When
        Page<PermissionResponse> result = permissionService.queryPermissions(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCode()).isEqualTo("user:create");
    }

    @Test
    void queryPermissions_WithSorting_ShouldReturnSortedResults() {
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

        PermissionQueryRequest request = new PermissionQueryRequest();
        request.setSortBy("name");
        request.setSortDirection("asc");
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<PermissionResponse> result = permissionService.queryPermissions(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getName()).isEqualTo("创建用户");
        assertThat(result.getContent().get(1).getName()).isEqualTo("更新用户");
    }

    @Test
    void queryPermissions_WithDescSorting_ShouldReturnDescSortedResults() {
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

        PermissionQueryRequest request = new PermissionQueryRequest();
        request.setSortBy("name");
        request.setSortDirection("desc");
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<PermissionResponse> result = permissionService.queryPermissions(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getName()).isEqualTo("更新用户");
        assertThat(result.getContent().get(1).getName()).isEqualTo("创建用户");
    }

    @Test
    void queryPermissions_WithPagination_ShouldReturnCorrectPage() {
        // Given
        for (int i = 1; i <= 5; i++) {
            Permission permission = Permission.builder()
                    .code("user:action" + i)
                    .name("用户操作" + i)
                    .build();
            permissionRepository.save(permission);
        }

        PermissionQueryRequest request = new PermissionQueryRequest();
        request.setPage(0);
        request.setPageSize(2);

        // When
        Page<PermissionResponse> result = permissionService.queryPermissions(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getNumber()).isEqualTo(0);
    }

    @Test
    void getAllCategories_ShouldReturnAllCategories() {
        // Given
        Permission permission1 = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission permission2 = Permission.builder()
                .code("user:update")
                .name("更新用户")
                .build();
        Permission permission3 = Permission.builder()
                .code("role:create")
                .name("创建角色")
                .build();
        permissionRepository.save(permission1);
        permissionRepository.save(permission2);
        permissionRepository.save(permission3);

        // When
        List<String> categories = permissionService.getAllCategories();

        // Then
        assertThat(categories).isNotNull();
        assertThat(categories).containsExactlyInAnyOrder("user", "role");
    }

    @Test
    void assignRoles_ShouldAssignRolesToPermission() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        // When
        PermissionResponse response = permissionService.assignRoles(savedPermission.getId(), 
                Set.of(adminRole.getId(), userRole.getId()));

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).hasSize(2);

        // Verify in database
        Permission updatedPermission = permissionRepository.findById(savedPermission.getId()).orElse(null);
        assertThat(updatedPermission).isNotNull();
        assertThat(updatedPermission.getRoles()).hasSize(2);
    }

    @Test
    void assignRoles_WithNonExistentPermission_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> permissionService.assignRoles(999L, Set.of(adminRole.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限不存在：999");
    }

    @Test
    void assignRoles_WithEmptyRoleIds_ShouldClearRoles() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .roles(Set.of(adminRole))
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        // When
        PermissionResponse response = permissionService.assignRoles(savedPermission.getId(), Set.of());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).isEmpty();

        // Verify in database
        Permission updatedPermission = permissionRepository.findById(savedPermission.getId()).orElse(null);
        assertThat(updatedPermission).isNotNull();
        assertThat(updatedPermission.getRoles()).isEmpty();
    }

    @Test
    void removeRoles_ShouldRemoveRolesFromPermission() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .roles(Set.of(adminRole, userRole))
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        // When
        PermissionResponse response = permissionService.removeRoles(savedPermission.getId(), 
                Set.of(adminRole.getId()));

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).hasSize(1);
        assertThat(response.getRoles().iterator().next().getName()).isEqualTo("USER");

        // Verify in database
        Permission updatedPermission = permissionRepository.findById(savedPermission.getId()).orElse(null);
        assertThat(updatedPermission).isNotNull();
        assertThat(updatedPermission.getRoles()).hasSize(1);
        assertThat(updatedPermission.getRoles().iterator().next().getName()).isEqualTo("USER");
    }

    @Test
    void removeRoles_WithNonExistentPermission_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> permissionService.removeRoles(999L, Set.of(adminRole.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限不存在：999");
    }

    @Test
    void removeRoles_WithNonExistentRoleIds_ShouldNotAffectPermission() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .roles(Set.of(adminRole))
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        // When
        PermissionResponse response = permissionService.removeRoles(savedPermission.getId(), 
                Set.of(999L)); // Non-existent role ID

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).hasSize(1); // Should remain unchanged
        assertThat(response.getRoles().iterator().next().getName()).isEqualTo("ADMIN");

        // Verify in database
        Permission updatedPermission = permissionRepository.findById(savedPermission.getId()).orElse(null);
        assertThat(updatedPermission).isNotNull();
        assertThat(updatedPermission.getRoles()).hasSize(1);
    }

    @Test
    void removeRoles_WithEmptyRoleIds_ShouldNotAffectPermission() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .roles(Set.of(adminRole))
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        // When
        PermissionResponse response = permissionService.removeRoles(savedPermission.getId(), Set.of());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).hasSize(1); // Should remain unchanged
        assertThat(response.getRoles().iterator().next().getName()).isEqualTo("ADMIN");
    }

    @Test
    void createPermission_WithComplexCode_ShouldExtractCorrectCategory() {
        // Given
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode("system:admin:manage");
        request.setName("系统管理");
        request.setRoleIds(Set.of());

        // When
        PermissionResponse response = permissionService.createPermission(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("system:admin:manage");
        assertThat(response.getCategory()).isEqualTo("system");
    }

    @Test
    void queryPermissions_WithMultipleFilters_ShouldReturnFilteredResults() {
        // Given
        Permission permission1 = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .roles(Set.of(adminRole))
                .build();
        Permission permission2 = Permission.builder()
                .code("user:update")
                .name("更新用户")
                .roles(Set.of(userRole))
                .build();
        Permission permission3 = Permission.builder()
                .code("role:create")
                .name("创建角色")
                .roles(Set.of(adminRole))
                .build();
        permissionRepository.save(permission1);
        permissionRepository.save(permission2);
        permissionRepository.save(permission3);

        PermissionQueryRequest request = new PermissionQueryRequest();
        request.setRoleId(adminRole.getId());
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<PermissionResponse> result = permissionService.queryPermissions(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCode()).isEqualTo("user:create");
    }
}






