package com.reajason.noone.server.shell;

import com.reajason.noone.Constants;
import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.exception.*;
import com.reajason.noone.server.admin.plugin.Plugin;
import com.reajason.noone.server.admin.plugin.PluginRepository;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
    private PluginRepository pluginRepository;

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
        shellConnectionPool.evict(id);
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
            ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
            boolean connected = connection.test();

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
    public Map<String, Object> testConfig(ShellTestConfigRequest request) {
        Shell tempShell = new Shell();
        tempShell.setUrl(request.getUrl());
        tempShell.setLanguage(request.getLanguage() != null ? request.getLanguage() : ShellLanguage.JAVA);
        tempShell.setProfileId(request.getProfileId());
        tempShell.setProxyUrl(request.getProxyUrl());
        tempShell.setCustomHeaders(request.getCustomHeaders());
        tempShell.setConnectTimeoutMs(request.getConnectTimeoutMs());
        tempShell.setReadTimeoutMs(request.getReadTimeoutMs());
        tempShell.setSkipSslVerify(request.getSkipSslVerify());
        tempShell.setMaxRetries(request.getMaxRetries());
        tempShell.setRetryDelayMs(request.getRetryDelayMs());
        try {
            ShellConnection connection = shellConnectionPool.createUncached(tempShell);
            return new HashMap<>() {{
                put("connected", connection.test());
            }};
        } catch (ShellRequestException e) {
            return failureResponse("Test failed: " + safeMessage(e), e);
        } catch (ShellResponseException e) {
            return failureResponse("Test failed: " + safeMessage(e), e);
        } catch (Exception e) {
            return failureResponse("Test failed: " + safeMessage(e), e);
        }
    }

    public Map<String, Object> dispatchPlugin(Long shellId, String pluginId, Map<String, Object> args) {
        Shell shell = getShellEntity(shellId);
        ShellLanguage shellLanguage = shell.getLanguage() != null ? shell.getLanguage() : ShellLanguage.JAVA;
        try {
            ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
            if (connection.needLoadPlugin(pluginId)) {
                Optional<Plugin> systemInfoPlugin = pluginRepository.findByPluginIdAndLanguage(pluginId, shellLanguage.getValue());
                systemInfoPlugin.ifPresent(plugin -> connection.loadPlugin(plugin.getPluginId(), pluginPayloadBytes(shellLanguage, plugin)));
            }
            Map<String, Object> result = connection.runPlugin(pluginId, args);
            Map<String, Object> response = handleShellConnectionResult(result);
            if (isSuccess(response.get(Constants.CODE))) {
                shellStatusUpdater.markConnected(shellId);
                if ("system-info".equals(pluginId)) {
                    tryExtractBasicInfo(shellId, response);
                }
            }
            return response;
        } catch (ShellRequestException e) {
            shellStatusUpdater.markError(shellId);
            return failureResponse("Dispatch failed: " + safeMessage(e), e);
        } catch (ShellResponseException e) {
            return failureResponse("Dispatch failed: " + safeMessage(e), e);
        } catch (Exception e) {
            return failureResponse("Dispatch failed: " + safeMessage(e), e);
        }
    }

    // ==================== Helper Methods ====================

    @SuppressWarnings("unchecked")
    private void tryExtractBasicInfo(Long shellId, Map<String, Object> response) {
        try {
            Object dataObj = response.get(Constants.DATA);
            if (!(dataObj instanceof Map)) {
                return;
            }
            Map<String, Object> data = (Map<String, Object>) dataObj;

            String rawOsName = SystemInfoNormalizer.extractString(data, "os", "name");
            String rawArch = SystemInfoNormalizer.extractString(data, "os", "arch");
            String runtimeType = SystemInfoNormalizer.extractString(data, "runtime", "type");
            String runtimeVersion = SystemInfoNormalizer.extractString(data, "runtime", "version");

            String os = SystemInfoNormalizer.normalizeOsName(rawOsName);
            String arch = SystemInfoNormalizer.normalizeArch(rawArch, os);

            Map<String, String> basicInfo = new HashMap<>();
            if (os != null) basicInfo.put("os", os);
            if (arch != null) basicInfo.put("arch", arch);
            if (runtimeType != null) basicInfo.put("runtimeType", runtimeType);
            if (runtimeVersion != null) basicInfo.put("runtimeVersion", runtimeVersion);

            if (!basicInfo.isEmpty()) {
                shellStatusUpdater.updateBasicInfo(shellId, basicInfo);
            }
        } catch (Exception e) {
            log.warn("Failed to extract basic info from system-info response: shellId={}", shellId, e);
        }
    }

    /**
     * Get shell entity and verify it exists
     */
    private Shell getShellEntity(Long shellId) {
        return shellRepository.findById(shellId)
                .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + shellId));
    }

    /**
     * Handle ShellConnection result and check for errors
     */
    private Map<String, Object> handleShellConnectionResult(Map<String, Object> result) {
        if (result == null) {
            throw new ResponseDecodeException("ShellConnection returned null result");
        }

        // Ensure a failure code exists when error details are present.
        if (result.containsKey(Constants.ERROR) && !result.containsKey(Constants.CODE)) {
            Map<String, Object> copy = new HashMap<>(result);
            copy.put(Constants.CODE, Constants.FAILURE);
            return copy;
        }

        return result;
    }

    private boolean isSuccess(Object codeObj) {
        if (!(codeObj instanceof Number code)) {
            return false;
        }
        return code.intValue() == Constants.SUCCESS;
    }

    private Map<String, Object> failureResponse(String message, Throwable e) {
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.CODE, Constants.FAILURE);
        response.put(Constants.ERROR, message != null ? message : "Unknown error");
        if (e != null) {
            response.put("errorType", e.getClass().getName());
            response.put("errorMessage", safeMessage(e));
            if (e instanceof ShellCommunicationException shellCommunicationException) {
                response.put("phase", shellCommunicationException.getPhase().name());
                response.put("retriable", shellCommunicationException.isRetriable());
            } else {
                response.put("phase", CommunicationPhase.INTERNAL.name());
                response.put("retriable", false);
            }
        }
        return response;
    }

    private String safeMessage(Throwable t) {
        if (t == null) {
            return "Unknown error";
        }
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return msg;
    }

    private byte[] pluginPayloadBytes(ShellLanguage shellLanguage, Plugin plugin) {
        if (plugin.getPayload() == null || plugin.getPayload().isBlank()) {
            throw new IllegalArgumentException("Plugin payload is empty: " + plugin.getPluginId());
        }

        return switch (shellLanguage) {
            case JAVA -> Base64.getDecoder().decode(plugin.getPayload());
            case NODEJS -> plugin.getPayload().getBytes(StandardCharsets.UTF_8);
        };
    }

}
