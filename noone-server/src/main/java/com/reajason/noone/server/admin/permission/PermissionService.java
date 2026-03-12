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
        Permission savedPermission = permissionRepository.save(permission);
        if (!CollectionUtils.isEmpty(request.getRoleIds())) {
            updateOwningRoles(savedPermission, new HashSet<>(roleRepository.findAllById(request.getRoleIds())));
        }
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

        if (request.getRoleIds() != null) {
            updateOwningRoles(permission, new HashSet<>(roleRepository.findAllById(request.getRoleIds())));
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
                .and(PermissionSpecifications.hasCategory(request.getCategory()))
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

        updateOwningRoles(permission, new HashSet<>(roleRepository.findAllById(roleIds)));
        Permission savedPermission = permissionRepository.save(permission);
        return permissionMapper.toResponse(savedPermission);
    }

    public PermissionResponse removeRoles(Long permissionId, Set<Long> roleIds) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("权限不存在：" + permissionId));

        Set<Role> remainingRoles = roleRepository.findAll().stream()
                .filter(role -> role.getPermissions().stream().anyMatch(existing -> existing.getId().equals(permissionId)))
                .filter(role -> !roleIds.contains(role.getId()))
                .collect(Collectors.toSet());
        updateOwningRoles(permission, remainingRoles);
        Permission savedPermission = permissionRepository.save(permission);
        return permissionMapper.toResponse(savedPermission);
    }

    private void updateOwningRoles(Permission permission, Set<Role> targetRoles) {
        Set<Role> currentRoles = roleRepository.findAll().stream()
                .filter(role -> role.getPermissions().stream().anyMatch(existing -> existing.getId().equals(permission.getId())))
                .collect(Collectors.toSet());

        currentRoles.stream()
                .filter(role -> !targetRoles.contains(role))
                .forEach(role -> role.getPermissions().removeIf(existing -> existing.getId().equals(permission.getId())));

        targetRoles.forEach(role -> role.getPermissions().add(permission));

        roleRepository.saveAll(currentRoles);
        roleRepository.saveAll(targetRoles);
        permission.setRoles(targetRoles);
    }
}
