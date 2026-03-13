package com.reajason.noone.server.admin.role;

import com.reajason.noone.server.admin.role.dto.RoleCreateRequest;
import com.reajason.noone.server.admin.role.dto.RoleQueryRequest;
import com.reajason.noone.server.admin.role.dto.RoleResponse;
import com.reajason.noone.server.admin.role.dto.RoleUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * 角色管理控制器
 * @author ReaJason
 * @since 2025/1/27
 */
@Slf4j
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @PreAuthorize("@authorizationService.hasSystemPermission('role:create')")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleCreateRequest request) {
        RoleResponse response = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('role:read')")
    public ResponseEntity<RoleResponse> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('role:update')")
    public ResponseEntity<RoleResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request) {
        RoleResponse response = roleService.updateRole(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('role:delete')")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasSystemPermission('role:list')")
    public ResponseEntity<Page<RoleResponse>> queryRoles(RoleQueryRequest request) {
        return ResponseEntity.ok(roleService.queryRoles(request));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("@authorizationService.hasSystemPermission('role:update')")
    public ResponseEntity<RoleResponse> assignPermissions(
            @PathVariable Long id,
            @RequestBody Set<Long> permissionIds) {
        RoleResponse response = roleService.assignPermissions(id, permissionIds);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/permissions")
    @PreAuthorize("@authorizationService.hasSystemPermission('role:update')")
    public ResponseEntity<RoleResponse> removePermissions(
            @PathVariable Long id,
            @RequestBody Set<Long> permissionIds) {
        RoleResponse response = roleService.removePermissions(id, permissionIds);
        return ResponseEntity.ok(response);
    }
}
