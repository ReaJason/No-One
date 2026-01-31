package com.reajason.noone.server.admin.role;

import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.admin.role.dto.RoleCreateRequest;
import com.reajason.noone.server.admin.role.dto.RoleQueryRequest;
import com.reajason.noone.server.admin.role.dto.RoleResponse;
import com.reajason.noone.server.admin.role.dto.RoleUpdateRequest;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;

    public RoleResponse createRole(RoleCreateRequest request) {
        if (roleRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("角色名称已存在：" + request.getName());
        }

        Role role = roleMapper.toEntity(request);

        if (!CollectionUtils.isEmpty(request.getPermissionIds())) {
            Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissionIds()));
            role.setPermissions(permissions);
        }

        Role savedRole = roleRepository.save(role);
        return roleMapper.toResponse(savedRole);
    }

    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + id));
        return roleMapper.toResponse(role);
    }

    public RoleResponse updateRole(Long id, RoleUpdateRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + id));

        if (StringUtils.isNotBlank(request.getName()) &&
                roleRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new IllegalArgumentException("角色名称已存在：" + request.getName());
        }

        roleMapper.updateEntity(role, request);

        // 处理权限关联更新
        if (request.getPermissionIds() != null) {
            Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissionIds()));
            role.setPermissions(permissions);
        } else {
            role.setPermissions(new HashSet<>(role.getPermissions()));
        }

        Role savedRole = roleRepository.save(role);
        return roleMapper.toResponse(savedRole);
    }

    public void deleteRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new IllegalArgumentException("角色不存在：" + id);
        }
        roleRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<RoleResponse> queryRoles(RoleQueryRequest request) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortOrder())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                request.getSortBy()
        ).and(Sort.by(Sort.Direction.ASC, "name"));

        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        Specification<Role> spec = RoleSpecifications.hasName(request.getName());

        return roleRepository.findAll(spec, pageable).map(roleMapper::toResponse);
    }

    public RoleResponse assignPermissions(Long roleId, Set<Long> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + roleId));

        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));
        role.setPermissions(permissions);

        Role savedRole = roleRepository.save(role);
        return roleMapper.toResponse(savedRole);
    }

    public RoleResponse removePermissions(Long roleId, Set<Long> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + roleId));

        role.setPermissions(role.getPermissions().stream()
                .filter(permission -> !permissionIds.contains(permission.getId()))
                .collect(Collectors.toSet()));

        Role savedRole = roleRepository.save(role);
        return roleMapper.toResponse(savedRole);
    }
}
