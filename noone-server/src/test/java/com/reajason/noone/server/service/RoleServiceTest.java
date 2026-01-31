package com.reajason.noone.server.service;

import com.reajason.noone.NooneApplication;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.role.RoleService;
import com.reajason.noone.server.admin.role.dto.RoleCreateRequest;
import com.reajason.noone.server.admin.role.dto.RoleQueryRequest;
import com.reajason.noone.server.admin.role.dto.RoleResponse;
import com.reajason.noone.server.admin.role.dto.RoleUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = NooneApplication.class)
@Import(TestPGContainerConfiguration.class)
@ActiveProfiles("test")
@Transactional
class RoleServiceTest {

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    private Permission readPermission;
    private Permission writePermission;
    private Permission deletePermission;

    @BeforeEach
    void setUp() {
        // Clean up existing data
        roleRepository.deleteAll();
        permissionRepository.deleteAll();

        // Create test permissions
        readPermission = Permission.builder()
                .code("user:read")
                .name("Read Users")
                .build();
        readPermission = permissionRepository.save(readPermission);

        writePermission = Permission.builder()
                .code("user:write")
                .name("Write Users")
                .build();
        writePermission = permissionRepository.save(writePermission);

        deletePermission = Permission.builder()
                .code("user:delete")
                .name("Delete Users")
                .build();
        deletePermission = permissionRepository.save(deletePermission);
    }

    @Test
    void createRole_ShouldCreateRoleSuccessfully() {
        // Given
        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("ADMIN");
        request.setPermissionIds(Set.of());

        // When
        RoleResponse response = roleService.createRole(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("ADMIN");
        assertThat(response.getId()).isNotNull();
        assertThat(response.getPermissions()).isEmpty();

        // Verify role is saved in database
        Role savedRole = roleRepository.findById(response.getId()).orElse(null);
        assertThat(savedRole).isNotNull();
        assertThat(savedRole.getName()).isEqualTo("ADMIN");
    }

    @Test
    void createRole_WithPermissions_ShouldCreateRoleWithPermissions() {
        // Given
        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("ADMIN");
        request.setPermissionIds(Set.of(readPermission.getId(), writePermission.getId()));

        // When
        RoleResponse response = roleService.createRole(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPermissions()).hasSize(2);
    }

    @Test
    void createRole_WithDuplicateName_ShouldThrowException() {
        // Given
        RoleCreateRequest request1 = new RoleCreateRequest();
        request1.setName("ADMIN");
        request1.setPermissionIds(Set.of());
        roleService.createRole(request1);

        RoleCreateRequest request2 = new RoleCreateRequest();
        request2.setName("ADMIN");
        request2.setPermissionIds(Set.of());

        // When & Then
        assertThatThrownBy(() -> roleService.createRole(request2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色名称已存在：ADMIN");
    }

    @Test
    void createRole_WithEmptyPermissionIds_ShouldCreateRoleWithoutPermissions() {
        // Given
        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("ADMIN");
        request.setPermissionIds(Set.of()); // Empty set

        // When
        RoleResponse response = roleService.createRole(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("ADMIN");
        assertThat(response.getPermissions()).isEmpty();
    }

    @Test
    void getRoleById_ShouldReturnRoleSuccessfully() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .permissions(Set.of(readPermission))
                .build();
        Role savedRole = roleRepository.save(role);

        // When
        RoleResponse response = roleService.getRoleById(savedRole.getId());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(savedRole.getId());
        assertThat(response.getName()).isEqualTo("ADMIN");
        assertThat(response.getPermissions()).hasSize(1);
    }

    @Test
    void getRoleById_WithNonExistentId_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> roleService.getRoleById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色不存在：999");
    }

    @Test
    void updateRole_ShouldUpdateRoleSuccessfully() {
        // Given
        Role role = Role.builder()
                .name("USER")
                .permissions(Set.of(readPermission))
                .build();
        Role savedRole = roleRepository.save(role);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("ADMIN");
        request.setPermissionIds(Set.of(writePermission.getId(), deletePermission.getId()));

        // When
        RoleResponse response = roleService.updateRole(savedRole.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("ADMIN");
        assertThat(response.getPermissions()).hasSize(2);
    }

    @Test
    void updateRole_WithDuplicateName_ShouldThrowException() {
        // Given
        Role existingRole = Role.builder()
                .name("ADMIN")
                .build();
        roleRepository.save(existingRole);

        Role role = Role.builder()
                .name("USER")
                .build();
        Role savedRole = roleRepository.save(role);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("ADMIN"); // Duplicate name

        // When & Then
        assertThatThrownBy(() -> roleService.updateRole(savedRole.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色名称已存在：ADMIN");
    }

    @Test
    void updateRole_WithNonExistentId_ShouldThrowException() {
        // Given
        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("ADMIN");

        // When & Then
        assertThatThrownBy(() -> roleService.updateRole(999L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色不存在：999");
    }

    @Test
    void updateRole_WithNullPermissionIds_ShouldNotUpdatePermissions() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .permissions(Set.of(readPermission))
                .build();
        Role savedRole = roleRepository.save(role);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("SUPER_ADMIN");
        request.setPermissionIds(null); // Null permission IDs

        // When
        RoleResponse response = roleService.updateRole(savedRole.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("SUPER_ADMIN");
        assertThat(response.getPermissions()).hasSize(1); // Should keep existing permissions
    }

    @Test
    void updateRole_WithEmptyPermissionIds_ShouldClearPermissions() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .permissions(Set.of(readPermission, writePermission))
                .build();
        Role savedRole = roleRepository.save(role);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("SUPER_ADMIN");
        request.setPermissionIds(Set.of()); // Empty permission IDs

        // When
        RoleResponse response = roleService.updateRole(savedRole.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("SUPER_ADMIN");
        assertThat(response.getPermissions()).isEmpty(); // Should clear permissions
    }

    @Test
    void deleteRole_ShouldDeleteRoleSuccessfully() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .build();
        Role savedRole = roleRepository.save(role);

        // When
        roleService.deleteRole(savedRole.getId());

        // Then
        assertThat(roleRepository.existsById(savedRole.getId())).isFalse();
    }

    @Test
    void deleteRole_WithNonExistentId_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> roleService.deleteRole(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色不存在：999");
    }

    @Test
    void queryRoles_ShouldReturnPaginatedResults() {
        // Given
        Role role1 = Role.builder()
                .name("ADMIN")
                .build();
        Role role2 = Role.builder()
                .name("USER")
                .build();
        roleRepository.save(role1);
        roleRepository.save(role2);

        RoleQueryRequest request = new RoleQueryRequest();
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<RoleResponse> result = roleService.queryRoles(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void queryRoles_WithNameFilter_ShouldReturnFilteredResults() {
        // Given
        Role adminRole = Role.builder()
                .name("ADMIN")
                .build();
        Role userRole = Role.builder()
                .name("USER")
                .build();
        roleRepository.save(adminRole);
        roleRepository.save(userRole);

        RoleQueryRequest request = new RoleQueryRequest();
        request.setName("ADMIN");
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<RoleResponse> result = roleService.queryRoles(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("ADMIN");
    }

    @Test
    void queryRoles_WithPartialNameFilter_ShouldReturnFilteredResults() {
        // Given
        Role adminRole = Role.builder()
                .name("ADMIN")
                .build();
        Role userRole = Role.builder()
                .name("USER")
                .build();
        roleRepository.save(adminRole);
        roleRepository.save(userRole);

        RoleQueryRequest request = new RoleQueryRequest();
        request.setName("ADM"); // Partial match
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<RoleResponse> result = roleService.queryRoles(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("ADMIN");
    }

    @Test
    void queryRoles_WithDateFilter_ShouldReturnFilteredResults() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Role role1 = Role.builder()
                .name("ADMIN")
                .build();
        roleRepository.save(role1);

        // Wait a bit to ensure different timestamps
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Role role2 = Role.builder()
                .name("USER")
                .build();
        roleRepository.save(role2);

        RoleQueryRequest request = new RoleQueryRequest();
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<RoleResponse> result = roleService.queryRoles(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void queryRoles_WithSorting_ShouldReturnSortedResults() {
        // Given
        Role role1 = Role.builder()
                .name("USER")
                .build();
        Role role2 = Role.builder()
                .name("ADMIN")
                .build();
        roleRepository.save(role1);
        roleRepository.save(role2);

        RoleQueryRequest request = new RoleQueryRequest();
        request.setSortBy("name");
        request.setSortOrder("asc");
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<RoleResponse> result = roleService.queryRoles(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getName()).isEqualTo("ADMIN");
        assertThat(result.getContent().get(1).getName()).isEqualTo("USER");
    }

    @Test
    void queryRoles_WithDescendingSort_ShouldReturnSortedResults() {
        // Given
        Role role1 = Role.builder()
                .name("ADMIN")
                .build();
        Role role2 = Role.builder()
                .name("USER")
                .build();
        roleRepository.save(role1);
        roleRepository.save(role2);

        RoleQueryRequest request = new RoleQueryRequest();
        request.setSortBy("name");
        request.setSortOrder("desc");
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<RoleResponse> result = roleService.queryRoles(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getName()).isEqualTo("USER");
        assertThat(result.getContent().get(1).getName()).isEqualTo("ADMIN");
    }

    @Test
    void assignPermissions_ShouldAssignPermissionsToRole() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .build();
        Role savedRole = roleRepository.save(role);

        Set<Long> permissionIds = Set.of(readPermission.getId(), writePermission.getId());

        // When
        RoleResponse response = roleService.assignPermissions(savedRole.getId(), permissionIds);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPermissions()).hasSize(2);
    }

    @Test
    void assignPermissions_WithNonExistentRole_ShouldThrowException() {
        // Given
        Set<Long> permissionIds = Set.of(readPermission.getId());

        // When & Then
        assertThatThrownBy(() -> roleService.assignPermissions(999L, permissionIds))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色不存在：999");
    }

    @Test
    void assignPermissions_WithEmptyPermissionIds_ShouldClearPermissions() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .permissions(Set.of(readPermission, writePermission))
                .build();
        Role savedRole = roleRepository.save(role);

        Set<Long> permissionIds = Set.of(); // Empty set

        // When
        RoleResponse response = roleService.assignPermissions(savedRole.getId(), permissionIds);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPermissions()).isEmpty();
    }

    @Test
    void removePermissions_ShouldRemovePermissionsFromRole() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .permissions(Set.of(readPermission, writePermission, deletePermission))
                .build();
        Role savedRole = roleRepository.save(role);

        Set<Long> permissionIdsToRemove = Set.of(readPermission.getId(), writePermission.getId());

        // When
        RoleResponse response = roleService.removePermissions(savedRole.getId(), permissionIdsToRemove);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPermissions()).hasSize(1);
    }

    @Test
    void removePermissions_WithNonExistentRole_ShouldThrowException() {
        // Given
        Set<Long> permissionIds = Set.of(readPermission.getId());

        // When & Then
        assertThatThrownBy(() -> roleService.removePermissions(999L, permissionIds))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色不存在：999");
    }

    @Test
    void removePermissions_WithNonExistentPermissionIds_ShouldNotAffectRole() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .permissions(Set.of(readPermission, writePermission))
                .build();
        Role savedRole = roleRepository.save(role);

        Set<Long> nonExistentPermissionIds = Set.of(999L, 998L);

        // When
        RoleResponse response = roleService.removePermissions(savedRole.getId(), nonExistentPermissionIds);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPermissions()).hasSize(2); // Should remain unchanged
    }

    @Test
    void removePermissions_WithEmptyPermissionIds_ShouldNotAffectRole() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .permissions(Set.of(readPermission, writePermission))
                .build();
        Role savedRole = roleRepository.save(role);

        Set<Long> emptyPermissionIds = Set.of();

        // When
        RoleResponse response = roleService.removePermissions(savedRole.getId(), emptyPermissionIds);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPermissions()).hasSize(2); // Should remain unchanged
    }

    @Test
    void createRole_WithNonExistentPermissionIds_ShouldCreateRoleWithoutThosePermissions() {
        // Given
        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("ADMIN");
        request.setPermissionIds(Set.of(999L, 998L)); // Non-existent permission IDs

        // When
        RoleResponse response = roleService.createRole(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("ADMIN");
        assertThat(response.getPermissions()).isEmpty(); // Should be empty since permission IDs don't exist
    }

    @Test
    void updateRole_WithNonExistentPermissionIds_ShouldUpdateRoleWithoutThosePermissions() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .permissions(Set.of(readPermission))
                .build();
        Role savedRole = roleRepository.save(role);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("SUPER_ADMIN");
        request.setPermissionIds(Set.of(999L, 998L)); // Non-existent permission IDs

        // When
        RoleResponse response = roleService.updateRole(savedRole.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("SUPER_ADMIN");
        assertThat(response.getPermissions()).isEmpty(); // Should be empty since permission IDs don't exist
    }
}
