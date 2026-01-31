package com.reajason.noone.server.admin.permission;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限分类管理控制器
 *
 * @author ReaJason
 * @since 2025/1/27
 */
@Slf4j
@RestController
@RequestMapping("/api/permission-categories")
@RequiredArgsConstructor
public class PermissionCategoryController {
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<String>> getAllCategories() {
        return ResponseEntity.ok(permissionService.getAllCategories());
    }
}
