package com.reajason.noone.server.shell;

import com.reajason.noone.server.shell.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    // ==================== Shell Management Endpoints ====================

    /**
     * Create a new shell connection
     * POST /api/shells
     */
    @PostMapping
    public ResponseEntity<ShellResponse> create(@Valid @RequestBody ShellCreateRequest request) {
        log.info("Creating shell: {}", request.getUrl());
        ShellResponse response = shellService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get shell by ID
     * GET /api/shells/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ShellResponse> getById(@PathVariable Long id) {
        log.info("Getting shell by id: {}", id);
        return ResponseEntity.ok(shellService.getById(id));
    }

    /**
     * Update shell connection
     * PUT /api/shells/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ShellResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ShellUpdateRequest request) {
        log.info("Updating shell id: {}", id);
        ShellResponse response = shellService.update(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete shell connection
     * DELETE /api/shells/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting shell id: {}", id);
        shellService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Query shells with pagination and filtering
     * GET /api/shells
     */
    @GetMapping
    public ResponseEntity<Page<ShellResponse>> query(ShellQueryRequest request) {
        log.info("Querying shells with filters: {}", request);
        return ResponseEntity.ok(shellService.query(request));
    }

    /**
     * Test shell connection
     * POST /api/shells/{id}/test
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        log.info("Testing connection for shell: {}", id);
        boolean connected = shellService.testConnection(id);
        return ResponseEntity.ok(Map.of(
                "connected", connected,
                "status", connected ? "CONNECTED" : "ERROR"));
    }

    /**
     * Test shell configuration without saving
     * POST /api/shells/test-config
     */
    @PostMapping("/test-config")
    public ResponseEntity<Map<String, Object>> testConfig(
            @Valid @RequestBody ShellTestConfigRequest request) {
        log.info("Testing shell config for URL: {}", request.getUrl());
        boolean connected = shellService.testConfig(request);
        return ResponseEntity.ok(Map.of(
                "connected", connected,
                "status", connected ? "CONNECTED" : "ERROR"));
    }

    // ==================== Shell Operation Endpoints ====================

    /**
     * Get system information
     * GET /api/shells/{id}/system-info
     */
    @GetMapping("/{id}/system-info")
    public ResponseEntity<Map<String, Object>> getSystemInfo(@PathVariable Long id) {
        log.info("Getting system info for shell: {}", id);
        Map<String, Object> systemInfo = shellService.getSystemInfo(id);
        return ResponseEntity.ok(systemInfo);
    }
}
