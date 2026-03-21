package com.reajason.noone.server.shell;

import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.exception.*;
import com.reajason.noone.server.audit.AuditAction;
import com.reajason.noone.server.audit.AuditLog;
import com.reajason.noone.server.audit.AuditModule;
import com.reajason.noone.server.shell.dto.*;
import com.reajason.noone.server.shell.oplog.ShellOpLog;
import com.reajason.noone.server.shell.oplog.ShellOperationType;
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
import java.util.*;

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
    private ShellConnectionPool shellConnectionPool;

    @Resource
    private ShellMapper shellMapper;

    @Resource
    private ShellResponseHelper shellResponseHelper;
    @Resource
    private ShellLookupHelper shellLookupHelper;
    @Resource
    private ShellCoreInitHelper shellCoreInitHelper;

    // ==================== Shell Management Operations ====================

    /**
     * Create a new shell connection (without automatic connection test)
     */
    @AuditLog(module = AuditModule.SHELL, action = AuditAction.CREATE, targetType = "Shell", targetId = "#result.id")
    public ShellResponse create(ShellCreateRequest request) {
        validateRuntimeConfig(request.getStaging(), request.getLoaderProfileId());
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
        Shell shell = shellLookupHelper.requireById(id);
        return shellMapper.toResponse(shell);
    }

    /**
     * Update shell connection
     */
    @AuditLog(module = AuditModule.SHELL, action = AuditAction.UPDATE, targetType = "Shell", targetId = "#id")
    public ShellResponse update(Long id, ShellUpdateRequest request) {
        Shell shell = shellLookupHelper.requireById(id);
        boolean staging = request.getStaging() != null ? request.getStaging() : Boolean.TRUE.equals(shell.getStaging());
        Long loaderProfileId = request.getLoaderProfileId() != null
                ? request.getLoaderProfileId()
                : shell.getLoaderProfileId();
        validateRuntimeConfig(staging, loaderProfileId);

        shellMapper.updateEntity(shell, request);
        Shell saved = shellRepository.save(shell);
        return shellMapper.toResponse(saved);
    }

    /**
     * Delete shell connection
     */
    @AuditLog(module = AuditModule.SHELL, action = AuditAction.DELETE, targetType = "Shell", targetId = "#id")
    public void delete(Long id) {
        Shell shell = shellLookupHelper.requireById(id);
        shellRepository.delete(shell);
        shellConnectionPool.evict(id);
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

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            ShellStatus status = ShellStatus.valueOf(request.getStatus().toUpperCase());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        if (request.getProjectId() != null && request.getProjectId() != 0) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("projectId"), request.getProjectId()));
        }

        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            String urlPattern = "%" + request.getUrl().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("url")), urlPattern));
        }

        if (request.getLanguage() != null && !request.getLanguage().isBlank()) {
            ShellLanguage language = ShellLanguage.fromJson(request.getLanguage());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("language"), language));
        }

        return shellRepository.findAll(spec, pageable).map(shellMapper::toResponse);
    }

    /**
     * Test shell connection
     */
    @ShellOpLog(operation = ShellOperationType.TEST, shellId = "#id")
    public boolean testConnection(Long id) {
        Shell shell = shellLookupHelper.requireById(id);

        try {
            ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
            shellCoreInitHelper.initCoreIfNeeded(connection, id);
            boolean connected = connection.test();

            if (connected) {
                shell.setStatus(ShellStatus.CONNECTED);
                shell.setLastOnlineAt(LocalDateTime.now());
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

    public Map<String, Object> initCore(Long id) {
        Shell shell = shellLookupHelper.requireById(id);

        boolean isStaging = Boolean.TRUE.equals(shell.getStaging());
        if (!isStaging) {
            return Map.of("success", true, "staging", false, "skipped", true, "durationMs", 0L);
        }

        long start = System.currentTimeMillis();
        try {
            ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
            shellCoreInitHelper.initCoreIfNeeded(connection, id);
            long durationMs = System.currentTimeMillis() - start;
            return Map.of("success", true, "staging", true, "skipped", false, "durationMs", durationMs);
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            log.error("Core injection failed for shell: {}", id, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("staging", true);
            response.put("skipped", false);
            response.put("durationMs", durationMs);
            response.put("error", shellResponseHelper.safeMessage(e));
            return response;
        }
    }

    @ShellOpLog(operation = ShellOperationType.TEST, shellId = "#id", action = "'ping'")
    public Map<String, Object> ping(Long id) {
        Shell shell = shellLookupHelper.requireById(id);

        long start = System.currentTimeMillis();
        boolean recoveryAttempted = false;
        boolean recovered = false;
        try {
            ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
            boolean connected;
            try {
                connected = connection.checkStatus();
            } catch (ShellCommunicationException firstProbeError) {
                if (!shouldAttemptPingRecovery(shell, firstProbeError)) {
                    throw firstProbeError;
                }
                recoveryAttempted = true;
                log.info("Ping status probe failed for shell {}, attempting core reinjection", id, firstProbeError);

                shellCoreInitHelper.initCoreIfNeeded(connection, id);

                connected = connection.checkStatus();
                recovered = true;
            }

            if (connected) {
                shell.setStatus(ShellStatus.CONNECTED);
                shell.setLastOnlineAt(LocalDateTime.now());
            } else {
                shell.setStatus(ShellStatus.ERROR);
            }
            shellRepository.save(shell);

            long durationMs = System.currentTimeMillis() - start;

            return Map.of(
                    "connected", connected,
                    "status", connected ? "CONNECTED" : "ERROR",
                    "latencyMs", durationMs,
                    "recoveryAttempted", recoveryAttempted,
                    "recovered", recovered
            );
        } catch (Exception e) {
            shell.setStatus(ShellStatus.ERROR);
            shellRepository.save(shell);
            log.error("Ping failed for shell: {}", id, e);

            long durationMs = System.currentTimeMillis() - start;

            Map<String, Object> response = new HashMap<>();
            response.put("connected", false);
            response.put("status", "ERROR");
            response.put("error", shellResponseHelper.safeMessage(e));
            response.put("latencyMs", durationMs);
            response.put("recoveryAttempted", recoveryAttempted);
            response.put("recovered", recovered);
            return response;
        }
    }

    /**
     * Test shell configuration without saving to database.
     * Used by create/edit pages to validate connection before saving.
     */
    public Map<String, Object> testConfig(ShellTestConfigRequest request) {
        validateRuntimeConfig(request.getStaging(), request.getLoaderProfileId());
        Shell tempShell = new Shell();
        tempShell.setUrl(request.getUrl());
        tempShell.setStaging(Boolean.TRUE.equals(request.getStaging()));
        tempShell.setShellType(request.getShellType());
        tempShell.setInterfaceName(request.getInterfaceName());
        tempShell.setLanguage(request.getLanguage() != null ? request.getLanguage() : ShellLanguage.JAVA);
        tempShell.setProfileId(request.getProfileId());
        tempShell.setLoaderProfileId(request.getLoaderProfileId());
        tempShell.setProxyUrl(request.getProxyUrl());
        tempShell.setCustomHeaders(request.getCustomHeaders());
        tempShell.setConnectTimeoutMs(request.getConnectTimeoutMs());
        tempShell.setReadTimeoutMs(request.getReadTimeoutMs());
        tempShell.setSkipSslVerify(request.getSkipSslVerify());
        tempShell.setMaxRetries(request.getMaxRetries());
        tempShell.setRetryDelayMs(request.getRetryDelayMs());
        try {
            ShellConnection connection = shellConnectionPool.createUncached(tempShell);
            connection.init();
            return new HashMap<>() {{
                put("connected", connection.test());
            }};
        } catch (Exception e) {
            log.error("Failed to test shell connection", e);
            return shellResponseHelper.failureResponse("Test failed: " + shellResponseHelper.safeMessage(e), e);
        }
    }

    private void validateRuntimeConfig(Boolean staging, Long loaderProfileId) {
        if (Boolean.TRUE.equals(staging) && loaderProfileId == null) {
            throw new IllegalArgumentException("Loader profile ID cannot be null when staging is enabled");
        }
    }

    private boolean shouldAttemptPingRecovery(Shell shell, ShellCommunicationException exception) {
        return Boolean.TRUE.equals(shell.getStaging()) && !(exception instanceof ResponseBusinessException);
    }

}
