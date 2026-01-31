package com.reajason.noone.server.shell;

import com.reajason.noone.core.JavaManager;
import com.reajason.noone.server.shell.dto.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Shell service with JavaManager integration
 *
 * @author ReaJason
 * @since 2025/12/27
 */
@Slf4j
@Service
@Transactional
public class ShellService {

    @Resource
    private ShellRepository shellRepository;

    @Resource
    private JavaManagerProvider javaManagerProvider;

    @Resource
    private ShellStatusUpdater shellStatusUpdater;

    @Resource
    private ShellMapper shellMapper;

    // ==================== Shell Management Operations ====================

    /**
     * Create a new shell connection (without automatic connection test)
     */
    public ShellResponse create(ShellCreateRequest request) {
        log.info("Creating shell connection: {}", request.getUrl());

        if (shellRepository.existsByUrl(request.getUrl())) {
            throw new IllegalArgumentException("Shell with URL already exists: " + request.getUrl());
        }

        Shell shell = shellMapper.toEntity(request);
        shell.setStatus(ShellStatus.DISCONNECTED);

        Shell saved = shellRepository.save(shell);
        return shellMapper.toResponse(saved);
    }

    /**
     * Get shell by ID
     */
    @Transactional(readOnly = true)
    public ShellResponse getById(Long id) {
        Shell shell = shellRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + id));
        return shellMapper.toResponse(shell);
    }

    /**
     * Update shell connection
     */
    public ShellResponse update(Long id, ShellUpdateRequest request) {
        Shell shell = shellRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + id));

        shellMapper.updateEntity(shell, request);
        Shell saved = shellRepository.save(shell);
        return shellMapper.toResponse(saved);
    }

    /**
     * Delete shell connection
     */
    public void delete(Long id) {
        Shell shell = shellRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + id));
        shellRepository.delete(shell);
        javaManagerProvider.evict(id);
        log.info("Deleted shell: {}", id);
    }

    /**
     * Query shells with pagination and filtering
     */
    @Transactional(readOnly = true)
    public Page<ShellResponse> query(ShellQueryRequest request) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortOrder()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy());

        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        Specification<Shell> spec = Specification.unrestricted();

        if (request.getGroup() != null && !request.getGroup().isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("group"), request.getGroup()));
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            ShellStatus status = ShellStatus.valueOf(request.getStatus().toUpperCase());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        if (request.getProjectId() != null && request.getProjectId() != 0) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("projectId"), request.getProjectId()));
        }

        return shellRepository.findAll(spec, pageable).map(shellMapper::toResponse);
    }

    /**
     * Test shell connection
     */
    public boolean testConnection(Long id) {
        Shell shell = shellRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + id));

        try {
            JavaManager manager = javaManagerProvider.getOrCreateCached(shell);
            boolean connected = manager.test();

            if (connected) {
                shell.setStatus(ShellStatus.CONNECTED);
                shell.setConnectTime(LocalDateTime.now());
            } else {
                shell.setStatus(ShellStatus.ERROR);
            }
            shellRepository.save(shell);

            return connected;
        } catch (Exception e) {
            shell.setStatus(ShellStatus.ERROR);
            shellRepository.save(shell);
            log.error("Connection test failed for shell: {}", id, e);
            return false;
        }
    }

    /**
     * Test shell configuration without saving to database.
     * Used by create/edit pages to validate connection before saving.
     */
    public boolean testConfig(ShellTestConfigRequest request) {
        Shell tempShell = new Shell();
        tempShell.setUrl(request.getUrl());
        tempShell.setProfileId(request.getProfileId());
        tempShell.setProxyUrl(request.getProxyUrl());
        tempShell.setCustomHeaders(request.getCustomHeaders());
        tempShell.setConnectTimeoutMs(request.getConnectTimeoutMs());
        tempShell.setReadTimeoutMs(request.getReadTimeoutMs());
        tempShell.setSkipSslVerify(request.getSkipSslVerify());
        tempShell.setMaxRetries(request.getMaxRetries());
        tempShell.setRetryDelayMs(request.getRetryDelayMs());

        try {
            JavaManager manager = javaManagerProvider.createUncached(tempShell);
            return manager.test();
        } catch (Exception e) {
            log.error("Config test failed for URL: {}", request.getUrl(), e);
            return false;
        }
    }

    // ==================== JavaManager Integration Operations ====================

    /**
     * Get system information
     */
    public Map<String, Object> getSystemInfo(Long shellId) {
        try {
            Shell shell = getShellEntity(shellId);
            JavaManager manager = javaManagerProvider.getOrCreateCached(shell);
            Map<String, Object> result = manager.getBasicInfo();
            Map<String, Object> response = handleJavaManagerResult(result);
            shellStatusUpdater.markConnected(shellId);
            return response;
        } catch (Exception e) {
            shellStatusUpdater.markError(shellId);
            throw e;
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Get shell entity and verify it exists
     */
    private Shell getShellEntity(Long shellId) {
        return shellRepository.findById(shellId)
                .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + shellId));
    }

    /**
     * Handle JavaManager result and check for errors
     */
    private Map<String, Object> handleJavaManagerResult(Map<String, Object> result) {
        if (result == null) {
            throw new RuntimeException("JavaManager returned null result");
        }

        // Check if result contains error
        if (result.containsKey("error")) {
            throw new RuntimeException("JavaManager error: " + result.get("error"));
        }

        return result;
    }

}
