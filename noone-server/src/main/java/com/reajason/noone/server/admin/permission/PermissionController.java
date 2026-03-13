package com.reajason.noone.server.admin.permission;

import com.reajason.noone.server.admin.permission.dto.PermissionCreateRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionQueryRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionResponse;
import com.reajason.noone.server.admin.permission.dto.PermissionUpdateRequest;
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
 * 权限管理控制器
 * @author ReaJason
 * @since 2025/1/27
 */
@Slf4j
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping
    @PreAuthorize("@authorizationService.hasSystemPermission('permission:create')")
    public ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody PermissionCreateRequest request) {
        PermissionResponse response = permissionService.createPermission(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('permission:read')")
    public ResponseEntity<PermissionResponse> getPermissionById(@PathVariable Long id) {
        return ResponseEntity.ok(permissionService.getPermissionById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('permission:update')")
    public ResponseEntity<PermissionResponse> updatePermission(
            @PathVariable Long id,
            @Valid @RequestBody PermissionUpdateRequest request) {
        PermissionResponse response = permissionService.updatePermission(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('permission:delete')")
    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasSystemPermission('permission:list')")
    public ResponseEntity<Page<PermissionResponse>> queryPermissions(PermissionQueryRequest request) {
        return ResponseEntity.ok(permissionService.queryPermissions(request));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("@authorizationService.hasSystemPermission('permission:update')")
    public ResponseEntity<PermissionResponse> assignRoles(
            @PathVariable Long id,
            @RequestBody Set<Long> roleIds) {
        PermissionResponse response = permissionService.assignRoles(id, roleIds);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/roles")
    @PreAuthorize("@authorizationService.hasSystemPermission('permission:update')")
    public ResponseEntity<PermissionResponse> removeRoles(
            @PathVariable Long id,
            @RequestBody Set<Long> roleIds) {
        PermissionResponse response = permissionService.removeRoles(id, roleIds);
        return ResponseEntity.ok(response);
    }
}
