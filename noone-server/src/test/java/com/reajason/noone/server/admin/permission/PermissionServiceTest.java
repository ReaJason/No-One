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

    @Captor
    private ArgumentCaptor<PermissionCreateRequest> createRequestCaptor;

    @Captor
    private ArgumentCaptor<PermissionUpdateRequest> updateRequestCaptor;

    @Test
    void shouldCreatePermission() {
        PermissionCreateRequest request = createRequest("user:create", "Create User");
        Permission entity = buildPermission(null, "user:create", "Create User");
        Permission saved = buildPermission(1L, "user:create", "Create User");
        PermissionResponse expectedResponse = buildResponse(1L, "user:create", "Create User");

        when(permissionRepository.existsByCodeAndDeletedFalse("user:create")).thenReturn(false);
        when(permissionMapper.toEntity(any(PermissionCreateRequest.class))).thenReturn(entity);
        when(permissionRepository.save(entity)).thenReturn(saved);
        when(permissionMapper.toResponse(saved)).thenReturn(expectedResponse);

        PermissionResponse response = permissionService.create(request);

        assertThat(response).isEqualTo(expectedResponse);
        verify(permissionMapper).toEntity(createRequestCaptor.capture());
        assertThat(createRequestCaptor.getValue()).isSameAs(request);
        verify(permissionRepository).save(entity);
    }

    @Test
    void shouldThrowWhenCreatingDuplicateCode() {
        when(permissionRepository.existsByCodeAndDeletedFalse("user:create")).thenReturn(true);

        assertThatThrownBy(() -> permissionService.create(createRequest("user:create", "Create User")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限代码已存在：user:create");

        verify(permissionRepository, never()).save(any());
    }

    @Test
    void shouldGetPermissionById() {
        Permission stored = buildPermission(10L, "user:read", "Read User");
        PermissionResponse expectedResponse = buildResponse(10L, "user:read", "Read User");

        when(permissionRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(stored));
        when(permissionMapper.toResponse(stored)).thenReturn(expectedResponse);

        PermissionResponse found = permissionService.getById(10L);

        assertThat(found).isEqualTo(expectedResponse);
    }

    @Test
    void shouldThrowWhenGettingNonExistentPermission() {
        when(permissionRepository.findByIdAndDeletedFalse(99999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.getById(99999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("权限不存在：99999");
    }

    @Test
    void shouldUpdatePermissionNameAndCode() {
        Permission stored = buildPermission(20L, "user:read", "Read User");
        Permission saved = buildPermission(20L, "user:update", "Update User");
        PermissionResponse expectedResponse = buildResponse(20L, "user:update", "Update User");

        when(permissionRepository.findByIdAndDeletedFalse(20L)).thenReturn(Optional.of(stored));
        when(permissionRepository.existsByCodeAndIdNotAndDeletedFalse("user:update", 20L)).thenReturn(false);
        when(permissionRepository.save(stored)).thenReturn(saved);
        when(permissionMapper.toResponse(saved)).thenReturn(expectedResponse);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setCode("user:update");
        request.setName("Update User");

        PermissionResponse updated = permissionService.update(20L, request);

        assertThat(updated).isEqualTo(expectedResponse);
        verify(permissionMapper).updateEntity(eq(stored), updateRequestCaptor.capture());
        assertThat(updateRequestCaptor.getValue()).isSameAs(request);
        verify(permissionRepository).save(stored);
    }

    @Test
    void shouldSkipCodeCheckWhenCodeIsBlank() {
        Permission stored = buildPermission(21L, "user:read", "Read User");
        Permission saved = buildPermission(21L, "user:read", "Read User Renamed");
        PermissionResponse expectedResponse = buildResponse(21L, "user:read", "Read User Renamed");

        when(permissionRepository.findByIdAndDeletedFalse(21L)).thenReturn(Optional.of(stored));
        when(permissionRepository.save(stored)).thenReturn(saved);
        when(permissionMapper.toResponse(saved)).thenReturn(expectedResponse);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setCode("   ");
        request.setName("Read User Renamed");

        PermissionResponse updated = permissionService.update(21L, request);

        assertThat(updated).isEqualTo(expectedResponse);
        verify(permissionRepository, never()).existsByCodeAndIdNotAndDeletedFalse(any(), any());
    }

    @Test
    void shouldThrowWhenUpdatingToExistingCode() {
        when(permissionRepository.findByIdAndDeletedFalse(30L))
                .thenReturn(Optional.of(buildPermission(30L, "user:read", "Read User")));
        when(permissionRepository.existsByCodeAndIdNotAndDeletedFalse("user:update", 30L)).thenReturn(true);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setCode("user:update");

        assertThatThrownBy(() -> permissionService.update(30L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限代码已存在：user:update");

        verify(permissionRepository, never()).save(any());
    }

    @Test
    void shouldDeletePermission() {
        Permission stored = buildPermission(40L, "user:delete", "Delete User");
        when(permissionRepository.findByIdAndDeletedFalse(40L)).thenReturn(Optional.of(stored));

        permissionService.delete(40L);

        assertThat(stored.getDeleted()).isTrue();
        verify(permissionRepository).save(stored);
        verify(permissionRepository, never()).deleteById(any());
    }

    @Test
    void shouldThrowWhenDeletingNonExistentPermission() {
        when(permissionRepository.findByIdAndDeletedFalse(99999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.delete(99999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("权限不存在：99999");

        verify(permissionRepository, never()).save(any());
    }

    @Test
    void shouldQueryWithFiltersAndAscSort() {
        Permission alpha = buildPermission(50L, "user:read", "Read User");
        Permission beta = buildPermission(51L, "user:update", "Update User");
        PermissionResponse alphaResp = buildResponse(50L, "user:read", "Read User");
        PermissionResponse betaResp = buildResponse(51L, "user:update", "Update User");

        Page<Permission> repositoryPage = new PageImpl<>(List.of(alpha, beta));
        when(permissionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(repositoryPage);
        when(permissionMapper.toResponse(alpha)).thenReturn(alphaResp);
        when(permissionMapper.toResponse(beta)).thenReturn(betaResp);

        PermissionQueryRequest query = new PermissionQueryRequest();
        query.setName("user");
        query.setCode("user");
        query.setRoleId(9L);
        query.setPage(1);
        query.setPageSize(5);
        query.setSortBy("createdAt");
        query.setSortDirection("asc");

        Page<PermissionResponse> page = permissionService.query(query);

        verify(permissionRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().toList())
                .extracting(order -> order.getProperty() + ":" + order.getDirection().name())
                .containsExactly("createdAt:ASC", "code:ASC");
        assertThat(page.getContent()).containsExactly(alphaResp, betaResp);
    }

    private PermissionCreateRequest createRequest(String code, String name) {
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode(code);
        request.setName(name);
        return request;
    }

    private Permission buildPermission(Long id, String code, String name) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setCode(code);
        permission.setName(name);
        permission.setDeleted(Boolean.FALSE);
        permission.setCreatedAt(LocalDateTime.of(2025, 1, 1, 10, 0));
        permission.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 10, 30));
        return permission;
    }

    private PermissionResponse buildResponse(Long id, String code, String name) {
        PermissionResponse response = new PermissionResponse();
        response.setId(id);
        response.setCode(code);
        response.setName(name);
        response.setCreatedAt(LocalDateTime.of(2025, 1, 1, 10, 0));
        response.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 10, 30));
        return response;
    }
}
