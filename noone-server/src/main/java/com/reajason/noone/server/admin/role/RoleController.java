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
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleCreateRequest request) {
        log.info("Creating role with name: {}", request.getName());
        RoleResponse response = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleResponse> getRoleById(@PathVariable Long id) {
        log.info("Getting role by id: {}", id);
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request) {
        log.info("Updating role with id: {}", id);
        RoleResponse response = roleService.updateRole(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        log.info("Deleting role with id: {}", id);
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<RoleResponse>> queryRoles(RoleQueryRequest request) {
        log.info("Querying roles with params: {}", request);
        return ResponseEntity.ok(roleService.queryRoles(request));
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<RoleResponse> assignPermissions(
            @PathVariable Long id,
            @RequestBody Set<Long> permissionIds) {
        log.info("Assigning permissions to role with id: {}, permissionIds: {}", id, permissionIds);
        RoleResponse response = roleService.assignPermissions(id, permissionIds);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/permissions")
    public ResponseEntity<RoleResponse> removePermissions(
            @PathVariable Long id,
            @RequestBody Set<Long> permissionIds) {
        log.info("Removing permissions from role with id: {}, permissionIds: {}", id, permissionIds);
        RoleResponse response = roleService.removePermissions(id, permissionIds);
        return ResponseEntity.ok(response);
    }
}
