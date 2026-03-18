package com.reajason.noone.server.admin.role;

import com.reajason.noone.server.api.ResourceNotFoundException;
import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.admin.role.dto.RoleCreateRequest;
import com.reajason.noone.server.admin.role.dto.RoleQueryRequest;
import com.reajason.noone.server.admin.role.dto.RoleResponse;
import com.reajason.noone.server.admin.role.dto.RoleUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RoleMapper roleMapper;

    @InjectMocks
    private RoleService roleService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @Captor
    private ArgumentCaptor<RoleCreateRequest> createRequestCaptor;

    @Captor
    private ArgumentCaptor<RoleUpdateRequest> updateRequestCaptor;

    @Test
    void shouldCreateRole() {
        RoleCreateRequest request = createRequest("Role Admin");
        Role entity = buildRole(null, "Role Admin");
        Role saved = buildRole(1L, "Role Admin");
        RoleResponse expectedResponse = buildResponse(1L, "Role Admin");

        when(roleRepository.existsByNameAndDeletedFalse("Role Admin")).thenReturn(false);
        when(roleMapper.toEntity(any(RoleCreateRequest.class))).thenReturn(entity);
        when(roleRepository.save(entity)).thenReturn(saved);
        when(roleMapper.toResponse(saved)).thenReturn(expectedResponse);

        RoleResponse response = roleService.create(request);

        assertThat(response).isEqualTo(expectedResponse);
        verify(roleMapper).toEntity(createRequestCaptor.capture());
        assertThat(createRequestCaptor.getValue()).isSameAs(request);
        verify(roleRepository).save(entity);
    }

    @Test
    void shouldThrowWhenCreatingDuplicateName() {
        when(roleRepository.existsByNameAndDeletedFalse("Role Admin")).thenReturn(true);

        assertThatThrownBy(() -> roleService.create(createRequest("Role Admin")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色名称已存在：Role Admin");

        verify(roleRepository, never()).save(any());
    }

    @Test
    void shouldGetRoleById() {
        Role stored = buildRole(10L, "Role Readonly");
        RoleResponse expectedResponse = buildResponse(10L, "Role Readonly");

        when(roleRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(stored));
        when(roleMapper.toResponse(stored)).thenReturn(expectedResponse);

        RoleResponse found = roleService.getById(10L);

        assertThat(found).isEqualTo(expectedResponse);
    }

    @Test
    void shouldThrowWhenGettingNonExistentRole() {
        when(roleRepository.findByIdAndDeletedFalse(99999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.getById(99999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("角色不存在：99999");
    }

    @Test
    void shouldUpdateRoleName() {
        Role stored = buildRole(20L, "Role Before");
        Role saved = buildRole(20L, "Role After");
        RoleResponse expectedResponse = buildResponse(20L, "Role After");

        when(roleRepository.findByIdAndDeletedFalse(20L)).thenReturn(Optional.of(stored));
        when(roleRepository.existsByNameAndIdNotAndDeletedFalse("Role After", 20L)).thenReturn(false);
        when(roleRepository.save(stored)).thenReturn(saved);
        when(roleMapper.toResponse(saved)).thenReturn(expectedResponse);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("Role After");

        RoleResponse updated = roleService.update(20L, request);

        assertThat(updated).isEqualTo(expectedResponse);
        verify(roleMapper).updateEntity(eq(stored), updateRequestCaptor.capture());
        assertThat(updateRequestCaptor.getValue()).isSameAs(request);
        verify(roleRepository).save(stored);
    }

    @Test
    void shouldSkipNameCheckWhenNameIsBlank() {
        Role stored = buildRole(21L, "Role Existing");
        Role saved = buildRole(21L, "Role Existing");
        RoleResponse expectedResponse = buildResponse(21L, "Role Existing");

        when(roleRepository.findByIdAndDeletedFalse(21L)).thenReturn(Optional.of(stored));
        when(roleRepository.save(stored)).thenReturn(saved);
        when(roleMapper.toResponse(saved)).thenReturn(expectedResponse);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("   ");

        RoleResponse updated = roleService.update(21L, request);

        assertThat(updated).isEqualTo(expectedResponse);
        verify(roleRepository, never()).existsByNameAndIdNotAndDeletedFalse(any(), any());
    }

    @Test
    void shouldThrowWhenUpdatingToExistingName() {
        when(roleRepository.findByIdAndDeletedFalse(30L))
                .thenReturn(Optional.of(buildRole(30L, "Role Current")));
        when(roleRepository.existsByNameAndIdNotAndDeletedFalse("Role Admin", 30L)).thenReturn(true);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("Role Admin");

        assertThatThrownBy(() -> roleService.update(30L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色名称已存在：Role Admin");

        verify(roleRepository, never()).save(any());
    }

    @Test
    void shouldDeleteRole() {
        Role stored = buildRole(40L, "Role Delete");
        when(roleRepository.findByIdAndDeletedFalse(40L)).thenReturn(Optional.of(stored));

        roleService.delete(40L);

        assertThat(stored.getDeleted()).isTrue();
        verify(roleRepository).save(stored);
        verify(roleRepository, never()).deleteById(any());
    }

    @Test
    void shouldThrowWhenDeletingNonExistentRole() {
        when(roleRepository.findByIdAndDeletedFalse(99999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.delete(99999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("角色不存在：99999");

        verify(roleRepository, never()).save(any());
    }

    @Test
    void shouldQueryWithFiltersAndAscSort() {
        Role alpha = buildRole(50L, "Role Alpha");
        Role beta = buildRole(51L, "Role Beta");
        RoleResponse alphaResp = buildResponse(50L, "Role Alpha");
        RoleResponse betaResp = buildResponse(51L, "Role Beta");

        Page<Role> repositoryPage = new PageImpl<>(List.of(alpha, beta));
        when(roleRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(repositoryPage);
        when(roleMapper.toResponse(alpha)).thenReturn(alphaResp);
        when(roleMapper.toResponse(beta)).thenReturn(betaResp);

        RoleQueryRequest query = new RoleQueryRequest();
        query.setName("Role");
        query.setPage(1);
        query.setPageSize(5);
        query.setSortBy("createdAt");
        query.setSortOrder("asc");

        Page<RoleResponse> page = roleService.query(query);

        verify(roleRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().toList())
                .extracting(order -> order.getProperty() + ":" + order.getDirection().name())
                .containsExactly("createdAt:ASC", "name:ASC");
        assertThat(page.getContent()).containsExactly(alphaResp, betaResp);
    }

    @Test
    void shouldSyncPermissionsAndFilterDeletedPermissions() {
        Role role = buildRole(60L, "Role Operator");
        Permission activeA = buildPermission(1L, "user:create", false);
        Permission activeB = buildPermission(2L, "user:update", false);
        Permission deleted = buildPermission(3L, "user:delete", true);
        RoleResponse expectedResponse = buildResponse(60L, "Role Operator");

        Set<Long> permissionIds = Set.of(1L, 2L, 3L);

        when(roleRepository.findByIdAndDeletedFalse(60L)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(permissionIds)).thenReturn(List.of(activeA, deleted, activeB));
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.toResponse(role)).thenReturn(expectedResponse);

        RoleResponse response = roleService.syncPermissions(60L, permissionIds);

        assertThat(response).isEqualTo(expectedResponse);
        assertThat(role.getPermissions()).containsExactlyInAnyOrder(activeA, activeB);
        verify(permissionRepository).findAllById(permissionIds);
        verify(roleRepository).save(role);
    }

    @Test
    void shouldSyncEmptyPermissionsWhenPermissionIdsIsNull() {
        Role role = buildRole(61L, "Role Auditor");
        role.setPermissions(Set.of(buildPermission(9L, "audit:read", false)));
        RoleResponse expectedResponse = buildResponse(61L, "Role Auditor");

        when(roleRepository.findByIdAndDeletedFalse(61L)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.toResponse(role)).thenReturn(expectedResponse);

        RoleResponse response = roleService.syncPermissions(61L, null);

        assertThat(response).isEqualTo(expectedResponse);
        assertThat(role.getPermissions()).isEmpty();
        verify(permissionRepository, never()).findAllById(any());
        verify(roleRepository).save(role);
    }

    private RoleCreateRequest createRequest(String name) {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setName(name);
        return request;
    }

    private Role buildRole(Long id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setDeleted(Boolean.FALSE);
        role.setCreatedAt(LocalDateTime.of(2025, 1, 1, 10, 0));
        role.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 10, 30));
        return role;
    }

    private Permission buildPermission(Long id, String code, boolean deleted) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setCode(code);
        permission.setName(code);
        permission.setDeleted(deleted);
        return permission;
    }

    private RoleResponse buildResponse(Long id, String name) {
        RoleResponse response = new RoleResponse();
        response.setId(id);
        response.setName(name);
        response.setCreatedAt(LocalDateTime.of(2025, 1, 1, 10, 0));
        response.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 10, 30));
        return response;
    }
}
