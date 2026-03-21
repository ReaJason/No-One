package com.reajason.noone.server.shell;

import com.reajason.noone.server.shell.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for shell operations
 *
 * @author ReaJason
 * @since 2025/12/27
 */
@Slf4j
@RestController
@RequestMapping("/api/shells")
@RequiredArgsConstructor
public class ShellController {

    private final ShellService shellService;

    @PostMapping
    @PreAuthorize("@authorizationService.hasSystemPermission('shell:create')")
    public ResponseEntity<ShellResponse> create(@Valid @RequestBody ShellCreateRequest request) {
        ShellResponse response = shellService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('shell:list')")
    public ResponseEntity<ShellResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(shellService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('shell:update')")
    public ResponseEntity<ShellResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ShellUpdateRequest request) {
        ShellResponse response = shellService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.hasSystemPermission('shell:delete')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        shellService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<ShellResponse>> query(ShellQueryRequest request) {
        return ResponseEntity.ok(shellService.query(request));
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("@authorizationService.hasSystemPermission('shell:test')")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        boolean connected = shellService.testConnection(id);
        return ResponseEntity.ok(Map.of(
                "connected", connected,
                "status", connected ? "CONNECTED" : "ERROR"));
    }

    @PostMapping("/{id}/init-core")
    @PreAuthorize("@authorizationService.hasSystemPermission('shell:test')")
    public ResponseEntity<Map<String, Object>> initCore(@PathVariable Long id) {
        return ResponseEntity.ok(shellService.initCore(id));
    }

    @PostMapping("/{id}/ping")
    @PreAuthorize("@authorizationService.hasSystemPermission('shell:test')")
    public ResponseEntity<Map<String, Object>> ping(@PathVariable Long id) {
        return ResponseEntity.ok(shellService.ping(id));
    }

    @PostMapping("/test-config")
    public ResponseEntity<Map<String, Object>> testConfig(
            @Valid @RequestBody ShellTestConfigRequest request) {
        return ResponseEntity.ok(shellService.testConfig(request));
    }
}
