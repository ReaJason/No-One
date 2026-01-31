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
    public ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody PermissionCreateRequest request) {
        log.info("Creating permission with code: {}", request.getCode());
        PermissionResponse response = permissionService.createPermission(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PermissionResponse> getPermissionById(@PathVariable Long id) {
        log.info("Getting permission by id: {}", id);
        return ResponseEntity.ok(permissionService.getPermissionById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PermissionResponse> updatePermission(
            @PathVariable Long id,
            @Valid @RequestBody PermissionUpdateRequest request) {
        log.info("Updating permission with id: {}", id);
        PermissionResponse response = permissionService.updatePermission(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
        log.info("Deleting permission with id: {}", id);
        permissionService.deletePermission(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<PermissionResponse>> queryPermissions(PermissionQueryRequest request) {
        log.info("Querying permissions with params: {}", request);
        return ResponseEntity.ok(permissionService.queryPermissions(request));
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<PermissionResponse> assignRoles(
            @PathVariable Long id,
            @RequestBody Set<Long> roleIds) {
        log.info("Assigning roles to permission with id: {}, roleIds: {}", id, roleIds);
        PermissionResponse response = permissionService.assignRoles(id, roleIds);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/roles")
    public ResponseEntity<PermissionResponse> removeRoles(
            @PathVariable Long id,
            @RequestBody Set<Long> roleIds) {
        log.info("Removing roles from permission with id: {}, roleIds: {}", id, roleIds);
        PermissionResponse response = permissionService.removeRoles(id, roleIds);
        return ResponseEntity.ok(response);
    }
}
