package com.reajason.noone.server.shell;

import com.reajason.noone.Constants;
import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.exception.*;
import com.reajason.noone.server.admin.plugin.Plugin;
import com.reajason.noone.server.admin.plugin.PluginRepository;
import com.reajason.noone.server.config.AuthorizationService;
import com.reajason.noone.server.shell.dto.*;
import com.reajason.noone.server.shell.oplog.ShellOperationLogService;
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

    @Resource
    private ShellOperationLogService shellOperationLogService;
    @Resource
    private AuthorizationService authorizationService;

    // ==================== Shell Management Operations ====================

    /**
     * Create a new shell connection (without automatic connection test)
     */
    public ShellResponse create(ShellCreateRequest request) {
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

//        if (!authorizationService.isAdmin()) {
//            var visibleProjectIds = authorizationService.getVisibleProjectIds();
//            if (visibleProjectIds.isEmpty()) {
//                spec = spec.and((root, query, cb) -> cb.disjunction());
//            } else {
//                spec = spec.and((root, query, cb) -> root.get("projectId").in(visibleProjectIds));
//            }
//        }

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
    public boolean testConnection(Long id) {
        Shell shell = shellRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + id));

        long start = System.currentTimeMillis();
        try {
            ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
            boolean connected = connection.test();

            if (connected) {
                shell.setStatus(ShellStatus.CONNECTED);
                shell.setLastOnlineAt(LocalDateTime.now());
            } else {
                shell.setStatus(ShellStatus.ERROR);
            }
            shellRepository.save(shell);

            long durationMs = System.currentTimeMillis() - start;
            shellOperationLogService.record(id, ShellOperationType.TEST, null, null,
                    null, Map.of("connected", connected), connected, null, durationMs);

            return connected;
        } catch (Exception e) {
            shell.setStatus(ShellStatus.ERROR);
            shellRepository.save(shell);
            log.error("Connection test failed for shell: {}", id, e);

            long durationMs = System.currentTimeMillis() - start;
            shellOperationLogService.record(id, ShellOperationType.TEST, null, null,
                    null, null, false, safeMessage(e), durationMs);

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

    private static final String TASK_MANAGER_PLUGIN_ID = "task-manager";

    public Map<String, Object> dispatchPlugin(Long shellId, String pluginId, Map<String, Object> args) {
        Shell shell = shellRepository.findById(shellId)
                .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + shellId));
        ShellLanguage shellLanguage = shell.getLanguage() != null ? shell.getLanguage() : ShellLanguage.JAVA;
        String action = args != null ? (String) args.get("action") : null;
        if (action == null) {
            action = args != null ? ((String) args.get("op")) : null;
        }
        long start = System.currentTimeMillis();
        try {
            ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);

            String runMode = resolveRunMode(pluginId, shellLanguage);
            boolean isTaskAction = action != null && action.startsWith("_task_");

            if (isTaskAction) {
                String taskOp = action.substring(6);
                return executeViaTaskManager(connection, shellLanguage, pluginId, taskOp, args, shell, shellId, start);
            }

            if ("async".equals(runMode)) {
                return executeViaTaskManager(connection, shellLanguage, pluginId, "submit", args, shell, shellId, start);
            }

            if ("scheduled".equals(runMode)) {
                return executeViaTaskManager(connection, shellLanguage, pluginId, "schedule", args, shell, shellId, start);
            }

            ensurePluginLoaded(connection, pluginId, shellLanguage);
            Map<String, Object> result = connection.runPlugin(pluginId, args);
            Map<String, Object> response = handleShellConnectionResult(result);
            if (isSuccess(response.get(Constants.CODE))) {
                shellStatusUpdater.markConnected(shellId);
                if ("system-info".equals(pluginId)) {
                    shell.setBasicInfo(((Map<String, Object>) response.get("data")));
                    shellRepository.save(shell);
                }
            }

            long durationMs = System.currentTimeMillis() - start;
            boolean success = isSuccess(response.get(Constants.CODE));
            String errorMsg = success ? null : String.valueOf(response.getOrDefault(Constants.ERROR, ""));
            shellOperationLogService.record(shellId, ShellOperationType.DISPATCH, pluginId, action,
                    args, response, success, errorMsg, durationMs);

            return response;
        } catch (ShellRequestException e) {
            shellStatusUpdater.markError(shellId);
            Map<String, Object> response = failureResponse("Dispatch failed: " + safeMessage(e), e);

            long durationMs = System.currentTimeMillis() - start;
            shellOperationLogService.record(shellId, ShellOperationType.DISPATCH, pluginId, action,
                    args, response, false, safeMessage(e), durationMs);

            return response;
        } catch (ShellResponseException e) {
            Map<String, Object> response = failureResponse("Dispatch failed: " + safeMessage(e), e);

            long durationMs = System.currentTimeMillis() - start;
            shellOperationLogService.record(shellId, ShellOperationType.DISPATCH, pluginId, action,
                    args, response, false, safeMessage(e), durationMs);

            return response;
        } catch (Exception e) {
            Map<String, Object> response = failureResponse("Dispatch failed: " + safeMessage(e), e);

            long durationMs = System.currentTimeMillis() - start;
            shellOperationLogService.record(shellId, ShellOperationType.DISPATCH, pluginId, action,
                    args, response, false, safeMessage(e), durationMs);

            return response;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeViaTaskManager(ShellConnection connection, ShellLanguage shellLanguage,
                                                      String pluginId, String taskOp,
                                                      Map<String, Object> originalArgs,
                                                      Shell shell, Long shellId, long start) {
        ensurePluginLoaded(connection, TASK_MANAGER_PLUGIN_ID, shellLanguage);
        ensurePluginLoaded(connection, pluginId, shellLanguage);

        Map<String, Object> taskManagerArgs = new HashMap<>();
        taskManagerArgs.put("op", taskOp);

        if ("submit".equals(taskOp) || "schedule".equals(taskOp)) {
            taskManagerArgs.put("targetPlugin", pluginId);
            taskManagerArgs.put("targetArgs", originalArgs);
            if ("schedule".equals(taskOp) && originalArgs != null) {
                Object interval = originalArgs.remove("_interval");
                if (interval != null) {
                    taskManagerArgs.put("delay", interval);
                    taskManagerArgs.put("period", interval);
                }
                Object delay = originalArgs.remove("_delay");
                if (delay != null) {
                    taskManagerArgs.put("delay", delay);
                }
            }
        } else {
            if (originalArgs != null && originalArgs.containsKey("taskId")) {
                taskManagerArgs.put("taskId", originalArgs.get("taskId"));
            }
        }

        Map<String, Object> result = connection.runPlugin(TASK_MANAGER_PLUGIN_ID, taskManagerArgs);
        Map<String, Object> response = handleShellConnectionResult(result);

        if (isSuccess(response.get(Constants.CODE))) {
            shellStatusUpdater.markConnected(shellId);
        }

        String action = "_task_" + taskOp;
        long durationMs = System.currentTimeMillis() - start;
        boolean success = isSuccess(response.get(Constants.CODE));
        String errorMsg = success ? null : String.valueOf(response.getOrDefault(Constants.ERROR, ""));
        shellOperationLogService.record(shellId, ShellOperationType.DISPATCH, pluginId, action,
                originalArgs, response, success, errorMsg, durationMs);

        return response;
    }

    private void ensurePluginLoaded(ShellConnection connection, String pluginId, ShellLanguage shellLanguage) {
        if (connection.needLoadPlugin(pluginId)) {
            Optional<Plugin> pluginOptional = pluginRepository.findByPluginIdAndLanguage(pluginId, shellLanguage.getValue());
            pluginOptional.ifPresent(plugin -> connection.loadPlugin(plugin.getPluginId(), pluginPayloadBytes(shellLanguage, plugin)));
        }
    }

    private String resolveRunMode(String pluginId, ShellLanguage shellLanguage) {
        Optional<Plugin> pluginOptional = pluginRepository.findByPluginIdAndLanguage(pluginId, shellLanguage.getValue());
        if (pluginOptional.isPresent()) {
            String runMode = pluginOptional.get().getRunMode();
            if (runMode != null && !runMode.isBlank()) {
                return runMode;
            }
        }
        return "sync";
    }

    // ==================== Helper Methods ====================
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
            case JAVA, DOTNET -> decodeBase64Payload(plugin, shellLanguage);
            case NODEJS -> plugin.getPayload().getBytes(StandardCharsets.UTF_8);
        };
    }

    private byte[] decodeBase64Payload(Plugin plugin, ShellLanguage shellLanguage) {
        try {
            return Base64.getDecoder().decode(plugin.getPayload());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid base64 payload for plugin [" + plugin.getPluginId() + "] language [" + shellLanguage.getValue() + "]",
                    e
            );
        }
    }
}
