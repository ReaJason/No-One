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

    @Test
    void shouldCreateRole() {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("Admin");
        Role entity = buildRole(1L, "Admin");
        RoleResponse response = buildResponse(1L, "Admin");

        when(roleRepository.existsByNameAndDeletedFalse("Admin")).thenReturn(false);
        when(roleMapper.toEntity(request)).thenReturn(entity);
        when(roleRepository.save(entity)).thenReturn(entity);
        when(roleMapper.toResponse(entity)).thenReturn(response);

        assertThat(roleService.create(request)).isEqualTo(response);
    }

    @Test
    void shouldThrowWhenRoleMissing() {
        when(roleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.getById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("角色不存在：1");
    }

    @Test
    void shouldUpdateRoleName() {
        Role role = buildRole(1L, "Admin");
        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("Ops");
        RoleResponse response = buildResponse(1L, "Ops");

        when(roleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(role));
        when(roleRepository.existsByNameAndIdNotAndDeletedFalse("Ops", 1L)).thenReturn(false);
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.toResponse(role)).thenReturn(response);

        assertThat(roleService.update(1L, request)).isEqualTo(response);
        verify(roleMapper).updateEntity(role, request);
    }

    @Test
    void shouldSoftDeleteRole() {
        Role role = buildRole(1L, "Admin");
        when(roleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(role));

        roleService.delete(1L);

        assertThat(role.getDeleted()).isTrue();
        verify(roleRepository).save(role);
    }

    @Test
    void shouldSyncPermissionsIgnoringDeletedPermissions() {
        Role role = buildRole(1L, "Admin");
        Permission active = Permission.builder().id(10L).name("Read").code("user:read").deleted(false).build();
        Permission deleted = Permission.builder().id(11L).name("Gone").code("user:gone").deleted(true).build();
        RoleResponse response = buildResponse(1L, "Admin");

        when(roleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(Set.of(10L, 11L))).thenReturn(List.of(active, deleted));
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.toResponse(role)).thenReturn(response);

        assertThat(roleService.syncPermissions(1L, Set.of(10L, 11L))).isEqualTo(response);
        assertThat(role.getPermissions()).containsExactly(active);
    }

    @Test
    void shouldQueryRoles() {
        Role role = buildRole(1L, "Admin");
        RoleResponse response = buildResponse(1L, "Admin");

        when(roleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(role)));
        when(roleMapper.toResponse(role)).thenReturn(response);

        RoleQueryRequest request = new RoleQueryRequest();
        request.setPage(1);
        request.setPageSize(5);
        request.setSortBy("createdAt");
        request.setSortOrder("desc");

        Page<RoleResponse> page = roleService.query(request);

        verify(roleRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(page.getContent()).containsExactly(response);
    }

    private Role buildRole(Long id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setDeleted(Boolean.FALSE);
        role.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        role.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 1, 0));
        return role;
    }

    private RoleResponse buildResponse(Long id, String name) {
        RoleResponse response = new RoleResponse();
        response.setId(id);
        response.setName(name);
        return response;
    }
}
