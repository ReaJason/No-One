package com.reajason.noone.server.admin.permission;

import com.reajason.noone.server.api.ResourceNotFoundException;
import com.reajason.noone.server.admin.permission.dto.PermissionCreateRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionQueryRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionResponse;
import com.reajason.noone.server.admin.permission.dto.PermissionUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    public PermissionResponse create(PermissionCreateRequest request) {
        if (permissionRepository.existsByCodeAndDeletedFalse(request.getCode())) {
            throw new IllegalArgumentException("权限代码已存在：" + request.getCode());
        }

        Permission permission = permissionMapper.toEntity(request);
        Permission savedPermission = permissionRepository.save(permission);
        return permissionMapper.toResponse(savedPermission);
    }

    @Transactional(readOnly = true)
    public PermissionResponse getById(Long id) {
        Permission permission = findActivePermission(id);
        return permissionMapper.toResponse(permission);
    }

    public PermissionResponse update(Long id, PermissionUpdateRequest request) {
        Permission permission = findActivePermission(id);

        if (StringUtils.isNotBlank(request.getCode()) &&
                permissionRepository.existsByCodeAndIdNotAndDeletedFalse(request.getCode(), id)) {
            throw new IllegalArgumentException("权限代码已存在：" + request.getCode());
        }

        permissionMapper.updateEntity(permission, request);

        Permission savedPermission = permissionRepository.save(permission);
        return permissionMapper.toResponse(savedPermission);
    }

    public void delete(Long id) {
        Permission permission = findActivePermission(id);
        permission.setDeleted(Boolean.TRUE);
        permissionRepository.save(permission);
    }

    @Transactional(readOnly = true)
    public Page<PermissionResponse> query(PermissionQueryRequest request) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortDirection())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                request.getSortBy()
        ).and(Sort.by(Sort.Direction.ASC, "code"));

        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        Specification<Permission> spec = PermissionSpecifications.hasName(request.getName())
                .and(PermissionSpecifications.hasCode(request.getCode()))
                .and(PermissionSpecifications.hasRole(request.getRoleId()))
                .and(PermissionSpecifications.notDeleted());

        return permissionRepository.findAll(spec, pageable).map(permissionMapper::toResponse);
    }

    private Permission findActivePermission(Long id) {
        return permissionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("权限不存在：" + id));
    }
}
