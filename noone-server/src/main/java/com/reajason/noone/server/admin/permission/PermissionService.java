package com.reajason.noone.server.admin.permission;

import com.reajason.noone.server.admin.permission.dto.PermissionCreateRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionQueryRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionResponse;
import com.reajason.noone.server.admin.permission.dto.PermissionUpdateRequest;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final PermissionMapper permissionMapper;

    public PermissionResponse createPermission(PermissionCreateRequest request) {
        if (permissionRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("权限代码已存在：" + request.getCode());
        }

        Permission permission = permissionMapper.toEntity(request);

        if (!CollectionUtils.isEmpty(request.getRoleIds())) {
            Set<Role> roles = new HashSet<>(roleRepository.findAllById(request.getRoleIds()));
            permission.setRoles(roles);
        }

        Permission savedPermission = permissionRepository.save(permission);
        return permissionMapper.toResponse(savedPermission);
    }

    @Transactional(readOnly = true)
    public PermissionResponse getPermissionById(Long id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("权限不存在：" + id));
        return permissionMapper.toResponse(permission);
    }

    public PermissionResponse updatePermission(Long id, PermissionUpdateRequest request) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("权限不存在：" + id));

        if (StringUtils.isNotBlank(request.getCode()) &&
                permissionRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new IllegalArgumentException("权限代码已存在：" + request.getCode());
        }

        permissionMapper.updateEntity(permission, request);

        // 处理角色关联更新
        if (request.getRoleIds() != null) {
            Set<Role> roles = new HashSet<>(roleRepository.findAllById(request.getRoleIds()));
            permission.setRoles(roles);
        } else {
            permission.setRoles(new HashSet<>(permission.getRoles()));
        }

        Permission savedPermission = permissionRepository.save(permission);
        return permissionMapper.toResponse(savedPermission);
    }

    public void deletePermission(Long id) {
        if (!permissionRepository.existsById(id)) {
            throw new IllegalArgumentException("权限不存在：" + id);
        }
        permissionRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<PermissionResponse> queryPermissions(PermissionQueryRequest request) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortDirection())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                request.getSortBy()
        ).and(Sort.by(Sort.Direction.ASC, "code"));

        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        Specification<Permission> spec = PermissionSpecifications.hasName(request.getName())
                .and(PermissionSpecifications.hasRole(request.getRoleId()));

        return permissionRepository.findAll(spec, pageable).map(permissionMapper::toResponse);
    }

    public List<String> getAllCategories() {
        return permissionRepository.findAll().stream()
                .map(p -> PermissionMapper.extractCategory(p.getCode()))
                .distinct()
                .collect(Collectors.toList());
    }

    public PermissionResponse assignRoles(Long permissionId, Set<Long> roleIds) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("权限不存在：" + permissionId));

        Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));
        permission.setRoles(roles);

        Permission savedPermission = permissionRepository.save(permission);
        return permissionMapper.toResponse(savedPermission);
    }

    public PermissionResponse removeRoles(Long permissionId, Set<Long> roleIds) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("权限不存在：" + permissionId));

        permission.setRoles(permission.getRoles().stream().filter(role -> !roleIds.contains(role.getId())).collect(Collectors.toSet()));

        Permission savedPermission = permissionRepository.save(permission);
        return permissionMapper.toResponse(savedPermission);
    }
}
