package com.reajason.noone.server.admin.permission;

import com.reajason.noone.server.api.ResourceNotFoundException;
import com.reajason.noone.server.admin.permission.dto.PermissionCreateRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionQueryRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionResponse;
import com.reajason.noone.server.admin.permission.dto.PermissionUpdateRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private PermissionService permissionService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @Test
    void shouldCreatePermission() {
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode("user:read");
        request.setName("Read User");
        Permission permission = buildPermission(1L, "user:read");
        PermissionResponse response = buildResponse(1L, "user:read");

        when(permissionRepository.existsByCodeAndDeletedFalse("user:read")).thenReturn(false);
        when(permissionMapper.toEntity(request)).thenReturn(permission);
        when(permissionRepository.save(permission)).thenReturn(permission);
        when(permissionMapper.toResponse(permission)).thenReturn(response);

        assertThat(permissionService.create(request)).isEqualTo(response);
    }

    @Test
    void shouldThrowWhenPermissionMissing() {
        when(permissionRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.getById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("权限不存在：1");
    }

    @Test
    void shouldUpdatePermissionWithoutManagingRoles() {
        Permission permission = buildPermission(1L, "user:read");
        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setName("Read Users");
        PermissionResponse response = buildResponse(1L, "user:read");

        when(permissionRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(permission));
        when(permissionRepository.save(permission)).thenReturn(permission);
        when(permissionMapper.toResponse(permission)).thenReturn(response);

        assertThat(permissionService.update(1L, request)).isEqualTo(response);
        verify(permissionMapper).updateEntity(permission, request);
    }

    @Test
    void shouldSoftDeletePermission() {
        Permission permission = buildPermission(1L, "user:read");
        when(permissionRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(permission));

        permissionService.delete(1L);

        assertThat(permission.getDeleted()).isTrue();
        verify(permissionRepository).save(permission);
    }

    @Test
    void shouldQueryPermissions() {
        Permission permission = buildPermission(1L, "user:read");
        PermissionResponse response = buildResponse(1L, "user:read");

        when(permissionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(permission)));
        when(permissionMapper.toResponse(permission)).thenReturn(response);

        PermissionQueryRequest request = new PermissionQueryRequest();
        request.setPage(1);
        request.setPageSize(5);
        request.setSortBy("createdAt");
        request.setSortDirection("desc");

        Page<PermissionResponse> page = permissionService.query(request);

        verify(permissionRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(page.getContent()).containsExactly(response);
    }

    private Permission buildPermission(Long id, String code) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setCode(code);
        permission.setName("Permission " + code);
        permission.setDeleted(Boolean.FALSE);
        permission.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        permission.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 1, 0));
        return permission;
    }

    private PermissionResponse buildResponse(Long id, String code) {
        PermissionResponse response = new PermissionResponse();
        response.setId(id);
        response.setCode(code);
        response.setName("Permission " + code);
        response.setCategory("user");
        return response;
    }
}
