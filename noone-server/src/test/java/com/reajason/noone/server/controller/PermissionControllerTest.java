package com.reajason.noone.server.controller;

import com.reajason.noone.NooneApplication;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.admin.permission.dto.PermissionCreateRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionResponse;
import com.reajason.noone.server.admin.permission.dto.PermissionUpdateRequest;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
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
class PermissionControllerTest {
    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    PermissionRepository permissionRepository;

    @Autowired
    RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        permissionRepository.deleteAll();
        roleRepository.deleteAll();
    }

    @Test
    void createPermission_ShouldReturnCreatedPermission() {
        // Given
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode("user:create");
        request.setName("创建用户");
        request.setRoleIds(Set.of());

        // When
        ResponseEntity<PermissionResponse> response = restTemplate.postForEntity(
                "/api/permissions", request, PermissionResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PermissionResponse permissionResponse = response.getBody();
        assertThat(permissionResponse).isNotNull();
        if (permissionResponse != null) {
            assertThat(permissionResponse.getCode()).isEqualTo("user:create");
            assertThat(permissionResponse.getName()).isEqualTo("创建用户");
            assertThat(permissionResponse.getCategory()).isEqualTo("user");
            assertThat(permissionResponse.getId()).isNotNull();
        }
    }

    @Test
    void createPermission_WithInvalidData_ShouldReturnBadRequest() {
        // Given
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode("invalid"); // Invalid format - should be prefix:action
        request.setName("a"); // Too short
        request.setRoleIds(Set.of());

        // When
        ResponseEntity<PermissionResponse> responseEntity = restTemplate.postForEntity(
                "/api/permissions", request, PermissionResponse.class);

        // Then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPermission_WithDuplicateCode_ShouldReturnBadRequest() {
        // Given
        Permission existingPermission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        permissionRepository.save(existingPermission);

        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode("user:create");
        request.setName("创建用户权限");
        request.setRoleIds(Set.of());

        // When
        ResponseEntity<PermissionResponse> responseEntity = restTemplate.postForEntity(
                "/api/permissions", request, PermissionResponse.class);

        // Then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPermission_WithEmptyCode_ShouldReturnBadRequest() {
        // Given
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode(""); // Empty code
        request.setName("创建用户");
        request.setRoleIds(Set.of());

        // When
        ResponseEntity<PermissionResponse> responseEntity = restTemplate.postForEntity(
                "/api/permissions", request, PermissionResponse.class);

        // Then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPermission_WithEmptyName_ShouldReturnBadRequest() {
        // Given
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode("user:create");
        request.setName(""); // Empty name
        request.setRoleIds(Set.of());

        // When
        ResponseEntity<PermissionResponse> responseEntity = restTemplate.postForEntity(
                "/api/permissions", request, PermissionResponse.class);

        // Then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPermission_WithRoles_ShouldCreatePermissionWithRoles() {
        // Given
        Role role = new Role();
        role.setName("ADMIN");
        Role savedRole = roleRepository.save(role);

        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode("user:create");
        request.setName("创建用户");
        request.setRoleIds(Set.of(savedRole.getId()));

        // When
        ResponseEntity<PermissionResponse> response = restTemplate.postForEntity(
                "/api/permissions", request, PermissionResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PermissionResponse permissionResponse = response.getBody();
        assertThat(permissionResponse).isNotNull();
        if (permissionResponse != null) {
            assertThat(permissionResponse.getRoles()).hasSize(1);
            assertThat(permissionResponse.getRoles().iterator().next().getName()).isEqualTo("ADMIN");
        }
    }

    @Test
    void getPermissionById_ShouldReturnPermission() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        // When
        ResponseEntity<PermissionResponse> response = restTemplate.getForEntity(
                "/api/permissions/" + savedPermission.getId(), PermissionResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PermissionResponse permissionResponse = response.getBody();
        assertThat(permissionResponse).isNotNull();
        if (permissionResponse != null) {
            assertThat(permissionResponse.getCode()).isEqualTo("user:create");
            assertThat(permissionResponse.getName()).isEqualTo("创建用户");
            assertThat(permissionResponse.getId()).isEqualTo(savedPermission.getId());
        }
    }

    @Test
    void getPermissionById_WithNonExistentId_ShouldReturnBadRequest() {
        // When
        ResponseEntity<PermissionResponse> responseEntity = restTemplate.getForEntity(
                "/api/permissions/999", PermissionResponse.class);

        // Then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatePermission_ShouldUpdatePermission() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setCode("user:update");
        request.setName("更新用户");

        // When
        ResponseEntity<PermissionResponse> response = restTemplate.exchange(
                "/api/permissions/" + savedPermission.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request),
                PermissionResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PermissionResponse permissionResponse = response.getBody();
        assertThat(permissionResponse).isNotNull();
        if (permissionResponse != null) {
            assertThat(permissionResponse.getCode()).isEqualTo("user:update");
            assertThat(permissionResponse.getName()).isEqualTo("更新用户");
            assertThat(permissionResponse.getCategory()).isEqualTo("user");
        }
    }

    @Test
    void updatePermission_WithInvalidData_ShouldReturnBadRequest() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setCode("invalid"); // Invalid format
        request.setName("a"); // Too short

        // When
        ResponseEntity<PermissionResponse> responseEntity = restTemplate.exchange(
                "/api/permissions/" + savedPermission.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request),
                PermissionResponse.class);

        // Then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatePermission_WithDuplicateCode_ShouldReturnBadRequest() {
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

        // When
        ResponseEntity<PermissionResponse> responseEntity = restTemplate.exchange(
                "/api/permissions/" + savedPermission2.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request),
                PermissionResponse.class);

        // Then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatePermission_WithNonExistentId_ShouldReturnBadRequest() {
        // Given
        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setCode("user:update");
        request.setName("更新用户");

        // When
        ResponseEntity<PermissionResponse> responseEntity = restTemplate.exchange(
                "/api/permissions/999",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                PermissionResponse.class);

        // Then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatePermission_WithRoles_ShouldUpdatePermissionWithRoles() {
        // Given
        Role role1 = new Role();
        role1.setName("ADMIN");
        Role role2 = new Role();
        role2.setName("USER");
        Role savedRole1 = roleRepository.save(role1);
        Role savedRole2 = roleRepository.save(role2);

        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setName("创建用户权限");
        request.setRoleIds(Set.of(savedRole1.getId(), savedRole2.getId()));

        // When
        ResponseEntity<PermissionResponse> response = restTemplate.exchange(
                "/api/permissions/" + savedPermission.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request),
                PermissionResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PermissionResponse permissionResponse = response.getBody();
        assertThat(permissionResponse).isNotNull();
        if (permissionResponse != null) {
            assertThat(permissionResponse.getName()).isEqualTo("创建用户权限");
            assertThat(permissionResponse.getRoles()).hasSize(2);
        }
    }

    @Test
    void deletePermission_ShouldDeletePermission() {
        // Given
        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        // When
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/permissions/" + savedPermission.getId(),
                HttpMethod.DELETE,
                null,
                Void.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(permissionRepository.existsById(savedPermission.getId())).isFalse();
    }

    @Test
    void deletePermission_WithNonExistentId_ShouldReturnBadRequest() {
        // When
        ResponseEntity<Void> responseEntity = restTemplate.exchange(
                "/api/permissions/999",
                HttpMethod.DELETE,
                null,
                Void.class);

        // Then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void queryPermissions_ShouldReturnPaginatedPermissions() {
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
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/permissions?page=0&size=10", String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify the response contains expected content
        assertThat(response.getBody()).contains("user:create");
        assertThat(response.getBody()).contains("user:update");
        // Verify pagination metadata is present
        assertThat(response.getBody()).contains("totalElements");
        assertThat(response.getBody()).contains("totalPages");
    }

    @Test
    void queryPermissions_WithCategoryFilter_ShouldReturnFilteredPermissions() {
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

        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/permissions?category=user", String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify the response contains the filtered permission
        assertThat(response.getBody()).contains("user:create");
        // Verify the response doesn't contain the other category
        assertThat(response.getBody()).doesNotContain("role:create");
    }

    @Test
    void queryPermissions_WithRoleFilter_ShouldReturnFilteredPermissions() {
        // Given
        Role role = new Role();
        role.setName("ADMIN");
        Role savedRole = roleRepository.save(role);

        Permission permission1 = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .roles(Set.of(savedRole))
                .build();
        Permission permission2 = Permission.builder()
                .code("user:update")
                .name("更新用户")
                .build();
        permissionRepository.save(permission1);
        permissionRepository.save(permission2);

        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/permissions?roleId=" + savedRole.getId(), String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify the response contains the permission with the role
        assertThat(response.getBody()).contains("user:create");
        // Verify the response doesn't contain the permission without the role
        assertThat(response.getBody()).doesNotContain("user:update");
    }

    @Test
    void queryPermissions_WithSorting_ShouldReturnSortedPermissions() {
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
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/permissions?sortBy=name&sortDirection=asc", String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify the response contains both permissions
        assertThat(response.getBody()).contains("user:create");
        assertThat(response.getBody()).contains("user:update");
    }

    @Test
    void assignRoles_ShouldAssignRolesToPermission() {
        // Given
        Role role1 = new Role();
        role1.setName("ADMIN");
        Role role2 = new Role();
        role2.setName("USER");
        Role savedRole1 = roleRepository.save(role1);
        Role savedRole2 = roleRepository.save(role2);

        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        // When
        ResponseEntity<PermissionResponse> response = restTemplate.exchange(
                "/api/permissions/" + savedPermission.getId() + "/roles",
                HttpMethod.PUT,
                new HttpEntity<>(Set.of(savedRole1.getId(), savedRole2.getId())),
                PermissionResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PermissionResponse permissionResponse = response.getBody();
        assertThat(permissionResponse).isNotNull();
        if (permissionResponse != null) {
            assertThat(permissionResponse.getRoles()).hasSize(2);
        }
    }

    @Test
    void assignRoles_WithNonExistentPermission_ShouldReturnBadRequest() {
        // Given
        Role role = new Role();
        role.setName("ADMIN");
        Role savedRole = roleRepository.save(role);

        // When
        ResponseEntity<PermissionResponse> responseEntity = restTemplate.exchange(
                "/api/permissions/999/roles",
                HttpMethod.PUT,
                new HttpEntity<>(Set.of(savedRole.getId())),
                PermissionResponse.class);

        // Then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void removeRoles_ShouldRemoveRolesFromPermission() {
        // Given
        Role role1 = new Role();
        role1.setName("ADMIN");
        Role role2 = new Role();
        role2.setName("USER");
        Role savedRole1 = roleRepository.save(role1);
        Role savedRole2 = roleRepository.save(role2);

        Permission permission = Permission.builder()
                .code("user:create")
                .name("创建用户")
                .roles(Set.of(savedRole1, savedRole2))
                .build();
        Permission savedPermission = permissionRepository.save(permission);

        // When
        ResponseEntity<PermissionResponse> response = restTemplate.exchange(
                "/api/permissions/" + savedPermission.getId() + "/roles",
                HttpMethod.DELETE,
                new HttpEntity<>(Set.of(savedRole1.getId())),
                PermissionResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PermissionResponse permissionResponse = response.getBody();
        assertThat(permissionResponse).isNotNull();
        if (permissionResponse != null) {
            assertThat(permissionResponse.getRoles()).hasSize(1);
            assertThat(permissionResponse.getRoles().iterator().next().getName()).isEqualTo("USER");
        }
    }

    @Test
    void removeRoles_WithNonExistentPermission_ShouldReturnBadRequest() {
        // Given
        Role role = new Role();
        role.setName("ADMIN");
        Role savedRole = roleRepository.save(role);

        // When
        ResponseEntity<PermissionResponse> responseEntity = restTemplate.exchange(
                "/api/permissions/999/roles",
                HttpMethod.DELETE,
                new HttpEntity<>(Set.of(savedRole.getId())),
                PermissionResponse.class);

        // Then
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}





