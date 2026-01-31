package com.reajason.noone.server.controller;

import com.reajason.noone.NooneApplication;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.role.dto.RoleCreateRequest;
import com.reajason.noone.server.admin.role.dto.RoleResponse;
import com.reajason.noone.server.admin.role.dto.RoleUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = NooneApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestPGContainerConfiguration.class)
@ActiveProfiles("test")
class RoleControllerTest {
    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PermissionRepository permissionRepository;

    @BeforeEach
    void setUp() {
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    void createRole_ShouldReturnCreatedRole() {
        // Given
        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("ADMIN");
        request.setPermissionIds(Set.of());

        // When
        ResponseEntity<RoleResponse> response = restTemplate.postForEntity(
                "/api/roles", request, RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RoleResponse roleResponse = response.getBody();
        assertThat(roleResponse).isNotNull();
        if (roleResponse != null) {
            assertThat(roleResponse.getName()).isEqualTo("ADMIN");
            assertThat(roleResponse.getId()).isNotNull();
            assertThat(roleResponse.getPermissions()).isEmpty();
        }
    }

    @Test
    void createRole_WithPermissions_ShouldReturnCreatedRoleWithPermissions() {
        // Given
        Permission permission1 = Permission.builder()
                .code("user:read")
                .name("Read Users")
                .build();
        Permission permission2 = Permission.builder()
                .code("user:write")
                .name("Write Users")
                .build();
        Permission savedPermission1 = permissionRepository.save(permission1);
        Permission savedPermission2 = permissionRepository.save(permission2);

        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("ADMIN");
        request.setPermissionIds(Set.of(savedPermission1.getId(), savedPermission2.getId()));

        // When
        ResponseEntity<RoleResponse> response = restTemplate.postForEntity(
                "/api/roles", request, RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RoleResponse roleResponse = response.getBody();
        assertThat(roleResponse).isNotNull();
        if (roleResponse != null) {
            assertThat(roleResponse.getName()).isEqualTo("ADMIN");
            assertThat(roleResponse.getPermissions()).hasSize(2);
        }
    }

    @Test
    void createRole_WithInvalidData_ShouldReturnBadRequest() {
        // Given
        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("A"); // Too short
        request.setPermissionIds(Set.of());

        // When
        ResponseEntity<RoleResponse> response = restTemplate.postForEntity(
                "/api/roles", request, RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRole_WithDuplicateName_ShouldReturnBadRequest() {
        // Given
        Role existingRole = Role.builder()
                .name("ADMIN")
                .build();
        roleRepository.save(existingRole);

        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("ADMIN");
        request.setPermissionIds(Set.of());

        // When
        ResponseEntity<RoleResponse> response = restTemplate.postForEntity(
                "/api/roles", request, RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getRoleById_ShouldReturnRole() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .build();
        Role savedRole = roleRepository.save(role);

        // When
        ResponseEntity<RoleResponse> response = restTemplate.getForEntity(
                "/api/roles/" + savedRole.getId(), RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RoleResponse roleResponse = response.getBody();
        assertThat(roleResponse).isNotNull();
        if (roleResponse != null) {
            assertThat(roleResponse.getName()).isEqualTo("ADMIN");
            assertThat(roleResponse.getId()).isEqualTo(savedRole.getId());
        }
    }

    @Test
    void getRoleById_WithNonExistentId_ShouldReturnBadRequest() {
        // When
        ResponseEntity<RoleResponse> response = restTemplate.getForEntity(
                "/api/roles/999", RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateRole_ShouldUpdateRole() {
        // Given
        Role role = Role.builder()
                .name("USER")
                .build();
        Role savedRole = roleRepository.save(role);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("ADMIN");

        // When
        ResponseEntity<RoleResponse> response = restTemplate.exchange(
                "/api/roles/" + savedRole.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request),
                RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RoleResponse roleResponse = response.getBody();
        assertThat(roleResponse).isNotNull();
        if (roleResponse != null) {
            assertThat(roleResponse.getName()).isEqualTo("ADMIN");
            assertThat(roleResponse.getId()).isEqualTo(savedRole.getId());
        }
    }

    @Test
    void updateRole_WithDuplicateName_ShouldReturnBadRequest() {
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

        // When
        ResponseEntity<RoleResponse> response = restTemplate.exchange(
                "/api/roles/" + savedRole.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request),
                RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateRole_WithNonExistentId_ShouldReturnBadRequest() {
        // Given
        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("ADMIN");

        // When
        ResponseEntity<RoleResponse> response = restTemplate.exchange(
                "/api/roles/999",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteRole_ShouldDeleteRole() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .build();
        Role savedRole = roleRepository.save(role);

        // When
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/roles/" + savedRole.getId(),
                HttpMethod.DELETE,
                null,
                Void.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(roleRepository.existsById(savedRole.getId())).isFalse();
    }

    @Test
    void deleteRole_WithNonExistentId_ShouldReturnBadRequest() {
        // When
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/roles/999",
                HttpMethod.DELETE,
                null,
                Void.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void queryRoles_ShouldReturnPaginatedRoles() {
        // Given
        Role role1 = Role.builder()
                .name("ADMIN")
                .build();
        Role role2 = Role.builder()
                .name("USER")
                .build();
        roleRepository.save(role1);
        roleRepository.save(role2);

        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/roles?page=0&size=10", String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify the response contains expected content
        assertThat(response.getBody()).contains("ADMIN");
        assertThat(response.getBody()).contains("USER");
        // Verify pagination metadata is present
        assertThat(response.getBody()).contains("totalElements");
        assertThat(response.getBody()).contains("totalPages");
    }

    @Test
    void queryRoles_WithNameFilter_ShouldReturnFilteredRoles() {
        // Given
        Role adminRole = Role.builder()
                .name("ADMIN")
                .build();
        Role userRole = Role.builder()
                .name("USER")
                .build();
        roleRepository.save(adminRole);
        roleRepository.save(userRole);

        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/roles?name=ADMIN", String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify the response contains the filtered role
        assertThat(response.getBody()).contains("ADMIN");
        // Verify the response doesn't contain the other role
        assertThat(response.getBody()).doesNotContain("USER");
    }

    @Test
    void assignPermissions_ShouldAssignPermissionsToRole() {
        // Given
        Role role = Role.builder()
                .name("ADMIN")
                .build();
        Role savedRole = roleRepository.save(role);

        Permission permission1 = Permission.builder()
                .code("user:read")
                .name("Read Users")
                .build();
        Permission permission2 = Permission.builder()
                .code("user:write")
                .name("Write Users")
                .build();
        Permission savedPermission1 = permissionRepository.save(permission1);
        Permission savedPermission2 = permissionRepository.save(permission2);

        Set<Long> permissionIds = Set.of(savedPermission1.getId(), savedPermission2.getId());

        // When
        ResponseEntity<RoleResponse> response = restTemplate.exchange(
                "/api/roles/" + savedRole.getId() + "/permissions",
                HttpMethod.PUT,
                new HttpEntity<>(permissionIds),
                RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RoleResponse roleResponse = response.getBody();
        assertThat(roleResponse).isNotNull();
        if (roleResponse != null) {
            assertThat(roleResponse.getPermissions()).hasSize(2);
        }
    }

    @Test
    void assignPermissions_WithNonExistentRole_ShouldReturnBadRequest() {
        // Given
        Set<Long> permissionIds = Set.of(1L, 2L);

        // When
        ResponseEntity<RoleResponse> response = restTemplate.exchange(
                "/api/roles/999/permissions",
                HttpMethod.PUT,
                new HttpEntity<>(permissionIds),
                RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void removePermissions_ShouldRemovePermissionsFromRole() {
        // Given
        Permission permission1 = Permission.builder()
                .code("user:read")
                .name("Read Users")
                .build();
        Permission permission2 = Permission.builder()
                .code("user:write")
                .name("Write Users")
                .build();
        Permission savedPermission1 = permissionRepository.save(permission1);
        Permission savedPermission2 = permissionRepository.save(permission2);

        Role role = Role.builder()
                .name("ADMIN")
                .permissions(Set.of(savedPermission1, savedPermission2))
                .build();
        Role savedRole = roleRepository.save(role);

        Set<Long> permissionIdsToRemove = Set.of(savedPermission1.getId());

        // When
        ResponseEntity<RoleResponse> response = restTemplate.exchange(
                "/api/roles/" + savedRole.getId() + "/permissions",
                HttpMethod.DELETE,
                new HttpEntity<>(permissionIdsToRemove),
                RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RoleResponse roleResponse = response.getBody();
        assertThat(roleResponse).isNotNull();
        if (roleResponse != null) {
            assertThat(roleResponse.getPermissions()).hasSize(1);
        }
    }

    @Test
    void removePermissions_WithNonExistentRole_ShouldReturnBadRequest() {
        // Given
        Set<Long> permissionIds = Set.of(1L, 2L);

        // When
        ResponseEntity<RoleResponse> response = restTemplate.exchange(
                "/api/roles/999/permissions",
                HttpMethod.DELETE,
                new HttpEntity<>(permissionIds),
                RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRole_WithEmptyPermissionIds_ShouldCreateRoleWithoutPermissions() {
        // Given
        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("ADMIN");
        request.setPermissionIds(Set.of()); // Empty set

        // When
        ResponseEntity<RoleResponse> response = restTemplate.postForEntity(
                "/api/roles", request, RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RoleResponse roleResponse = response.getBody();
        assertThat(roleResponse).isNotNull();
        if (roleResponse != null) {
            assertThat(roleResponse.getName()).isEqualTo("ADMIN");
            assertThat(roleResponse.getPermissions()).isEmpty();
        }
    }

    @Test
    void updateRole_WithNullPermissionIds_ShouldNotUpdatePermissions() {
        // Given
        Permission permission = Permission.builder()
                .code("user:read")
                .name("Read Users")
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        Role role = Role.builder()
                .name("ADMIN")
                .permissions(Set.of(savedPermission))
                .build();
        Role savedRole = roleRepository.save(role);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("SUPER_ADMIN");
        request.setPermissionIds(null); // Null permission IDs

        // When
        ResponseEntity<RoleResponse> response = restTemplate.exchange(
                "/api/roles/" + savedRole.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request),
                RoleResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RoleResponse roleResponse = response.getBody();
        assertThat(roleResponse).isNotNull();
        if (roleResponse != null) {
            assertThat(roleResponse.getName()).isEqualTo("SUPER_ADMIN");
            assertThat(roleResponse.getPermissions()).hasSize(1); // Should keep existing permissions
        }
    }
}
