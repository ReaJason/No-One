package com.reajason.noone.server.admin.role;

import com.reajason.noone.server.api.ResourceNotFoundException;
import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.admin.role.dto.RoleCreateRequest;
import com.reajason.noone.server.admin.role.dto.RoleQueryRequest;
import com.reajason.noone.server.admin.role.dto.RoleResponse;
import com.reajason.noone.server.admin.role.dto.RoleUpdateRequest;
import com.reajason.noone.server.audit.AuditAction;
import com.reajason.noone.server.audit.AuditLog;
import com.reajason.noone.server.audit.AuditModule;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;

    @AuditLog(module = AuditModule.ROLE, action = AuditAction.CREATE, targetType = "Role", targetId = "#result.id")
    public RoleResponse create(RoleCreateRequest request) {
        if (roleRepository.existsByNameAndDeletedFalse(request.getName())) {
            throw new IllegalArgumentException("角色名称已存在：" + request.getName());
        }

        Role role = roleMapper.toEntity(request);
        Role savedRole = roleRepository.save(role);
        return roleMapper.toResponse(savedRole);
    }

    @Transactional(readOnly = true)
    public RoleResponse getById(Long id) {
        Role role = findActiveRole(id);
        return roleMapper.toResponse(role);
    }

    @AuditLog(module = AuditModule.ROLE, action = AuditAction.UPDATE, targetType = "Role", targetId = "#id")
    public RoleResponse update(Long id, RoleUpdateRequest request) {
        Role role = findActiveRole(id);

        if (StringUtils.isNotBlank(request.getName()) &&
                roleRepository.existsByNameAndIdNotAndDeletedFalse(request.getName(), id)) {
            throw new IllegalArgumentException("角色名称已存在：" + request.getName());
        }

        roleMapper.updateEntity(role, request);

        Role savedRole = roleRepository.save(role);
        return roleMapper.toResponse(savedRole);
    }

    @AuditLog(module = AuditModule.ROLE, action = AuditAction.DELETE, targetType = "Role", targetId = "#id")
    public void delete(Long id) {
        Role role = findActiveRole(id);
        role.setDeleted(Boolean.TRUE);
        roleRepository.save(role);
    }

    @Transactional(readOnly = true)
    public Page<RoleResponse> query(RoleQueryRequest request) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortOrder())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                request.getSortBy()
        ).and(Sort.by(Sort.Direction.ASC, "name"));

        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        Specification<Role> spec = RoleSpecifications.hasName(request.getName())
                .and(RoleSpecifications.notDeleted());

        return roleRepository.findAll(spec, pageable).map(roleMapper::toResponse);
    }

    @AuditLog(module = AuditModule.ROLE, action = AuditAction.UPDATE, targetType = "Role", targetId = "#id", description = "'Sync permissions'")
    public RoleResponse syncPermissions(Long id, Set<Long> permissionIds) {
        Role role = findActiveRole(id);
        Set<Permission> permissions = permissionIds == null
                ? new HashSet<>()
                : permissionRepository.findAllById(permissionIds).stream()
                        .filter(permission -> !Boolean.TRUE.equals(permission.getDeleted()))
                        .collect(java.util.stream.Collectors.toSet());
        role.setPermissions(permissions);
        return roleMapper.toResponse(roleRepository.save(role));
    }

    private Role findActiveRole(Long id) {
        return roleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("角色不存在：" + id));
    }
}
