package com.reajason.noone.server.shell;

import com.reajason.noone.Constants;
import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.exception.*;
import com.reajason.noone.server.plugin.BuiltinPluginRegistryService;
import com.reajason.noone.server.plugin.Plugin;
import com.reajason.noone.server.plugin.JavaPluginPayloadService;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
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
    private ShellStatusUpdater shellStatusUpdater;

    @Resource
    private ShellMapper shellMapper;

    @Resource
    private ShellOperationLogService shellOperationLogService;
    @Resource
    private AuthorizationService authorizationService;
    @Resource
    private JavaPluginPayloadService javaPluginPayloadService;
    @Resource
    private BuiltinPluginRegistryService builtinPluginRegistryService;

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
        Plugin plugin = findPlugin(pluginId, shellLanguage).orElse(null);
        String action = args != null ? (String) args.get("action") : null;
        if (action == null) {
            action = args != null ? ((String) args.get("op")) : null;
        }
        long start = System.currentTimeMillis();
        try {
            ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
            ensurePluginCacheSnapshot(connection);

            String runMode = resolveRunMode(plugin);
            boolean isTaskAction = action != null && action.startsWith("_task_");

            if (isTaskAction) {
                String taskOp = action.substring(6);
                return executeViaTaskManager(connection, shellLanguage, plugin, pluginId, taskOp, args, shellId, start);
            }

            if ("async".equals(runMode)) {
                return executeViaTaskManager(connection, shellLanguage, plugin, pluginId, "submit", args, shellId, start);
            }

            if ("scheduled".equals(runMode)) {
                return executeViaTaskManager(connection, shellLanguage, plugin, pluginId, "schedule", args, shellId, start);
            }

            ensurePluginLoaded(connection, plugin, shellLanguage);
            Map<String, Object> result = connection.runPlugin(pluginId, args);
            Map<String, Object> response = handleShellConnectionResult(result);
            if (isSuccess(response.get(Constants.CODE))) {
                shellStatusUpdater.markConnected(shellId);
                if ("system-info".equals(pluginId)) {
                    shell.setBasicInfo(((Map<String, Object>) response.get("data")));
                    shellRepository.save(shell);
                }
            }

            recordDispatch(shellId, pluginId, action, args, response, start);
            return response;
        } catch (ShellRequestException e) {
            shellStatusUpdater.markError(shellId);
            Map<String, Object> response = failureResponse("Dispatch failed: " + safeMessage(e), e);
            recordDispatch(shellId, pluginId, action, args, response, start);
            return response;
        } catch (ShellResponseException e) {
            Map<String, Object> response = failureResponse("Dispatch failed: " + safeMessage(e), e);
            recordDispatch(shellId, pluginId, action, args, response, start);
            return response;
        } catch (Exception e) {
            Map<String, Object> response = failureResponse("Dispatch failed: " + safeMessage(e), e);
            recordDispatch(shellId, pluginId, action, args, response, start);
            return response;
        }
    }

    @Transactional(readOnly = true)
    public ShellPluginStatusResponse getPluginStatus(Long shellId, String pluginId) {
        Shell shell = shellRepository.findById(shellId)
                .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + shellId));
        ShellLanguage shellLanguage = shell.getLanguage() != null ? shell.getLanguage() : ShellLanguage.JAVA;
        Plugin plugin = findPlugin(pluginId, shellLanguage)
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + pluginId));
        ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
        return toPluginStatus(plugin, connection);
    }

    public ShellPluginStatusResponse updatePlugin(Long shellId, String pluginId) {
        Shell shell = shellRepository.findById(shellId)
                .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + shellId));
        ShellLanguage shellLanguage = shell.getLanguage() != null ? shell.getLanguage() : ShellLanguage.JAVA;
        Plugin plugin = findPlugin(pluginId, shellLanguage)
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + pluginId));
        ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
        loadPlugin(connection, plugin, shellLanguage, true);
        return toPluginStatus(plugin, connection);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeViaTaskManager(ShellConnection connection, ShellLanguage shellLanguage,
                                                      Plugin plugin, String pluginId, String taskOp,
                                                      Map<String, Object> originalArgs,
                                                      Long shellId, long start) {
        ensureInfrastructurePluginLoaded(connection, TASK_MANAGER_PLUGIN_ID, shellLanguage);
        boolean requiresTargetPlugin = "submit".equals(taskOp) || "schedule".equals(taskOp);
        if (requiresTargetPlugin) {
            ensurePluginLoaded(connection, plugin, shellLanguage);
        }

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

        recordDispatch(shellId, pluginId, "_task_" + taskOp, originalArgs, response, start);
        return response;
    }

    private void ensurePluginLoaded(ShellConnection connection, Plugin plugin, ShellLanguage shellLanguage) {
        if (plugin != null && connection.needLoadPlugin(plugin.getPluginId())) {
            loadPlugin(connection, plugin, shellLanguage, false);
        }
    }

    private void ensureInfrastructurePluginLoaded(ShellConnection connection, String pluginId, ShellLanguage shellLanguage) {
        Optional<Plugin> pluginOptional = findPlugin(pluginId, shellLanguage);
        if (pluginOptional.isEmpty()) {
            return;
        }
        Plugin plugin = pluginOptional.get();
        if (connection.needLoadPlugin(pluginId)) {
            loadPlugin(connection, plugin, shellLanguage, false);
            return;
        }
        if (connection.isPluginOutdated(pluginId, plugin.getVersion())) {
            loadPlugin(connection, plugin, shellLanguage, true);
        }
    }

    private void ensurePluginCacheSnapshot(ShellConnection connection) {
        if (!connection.isPluginCacheInitialized()) {
            connection.test();
        }
    }

    private Optional<Plugin> findPlugin(String pluginId, ShellLanguage shellLanguage) {
        return builtinPluginRegistryService.findOrRegister(pluginId, shellLanguage.getValue());
    }

    private String resolveRunMode(Plugin plugin) {
        if (plugin != null) {
            String runMode = plugin.getRunMode();
            if (runMode != null && !runMode.isBlank()) {
                return runMode;
            }
        }
        return "sync";
    }

    private ShellPluginStatusResponse toPluginStatus(Plugin plugin, ShellConnection connection) {
        String shellVersion = connection.getLoadedPluginVersion(plugin.getPluginId());
        return ShellPluginStatusResponse.builder()
                .pluginId(plugin.getPluginId())
                .serverVersion(plugin.getVersion())
                .shellVersion(shellVersion)
                .loaded(shellVersion != null || !connection.needLoadPlugin(plugin.getPluginId()))
                .needsUpdate(!plugin.getVersion().equals(shellVersion))
                .build();
    }

    private void loadPlugin(ShellConnection connection, Plugin plugin, ShellLanguage shellLanguage, boolean forceRefresh) {
        byte[] payloadBytes = pluginPayloadBytes(shellLanguage, plugin);
        if (shellLanguage != ShellLanguage.JAVA) {
            if (forceRefresh) {
                connection.refreshPlugin(plugin.getPluginId(), plugin.getVersion(), payloadBytes);
            } else {
                connection.loadPlugin(plugin.getPluginId(), plugin.getVersion(), payloadBytes);
            }
            return;
        }

        List<Exception> failures = new ArrayList<>();
        for (JavaPluginPayloadService.JavaPluginCandidate candidate : javaPluginPayloadService.buildCandidates(plugin, payloadBytes)) {
            try {
                if (forceRefresh) {
                    connection.refreshPlugin(plugin.getPluginId(), plugin.getVersion(), candidate.payloadBytes());
                } else {
                    connection.loadPlugin(plugin.getPluginId(), plugin.getVersion(), candidate.payloadBytes());
                }
                return;
            } catch (ResponseBusinessException e) {
                failures.add(e);
                if (isDuplicateJavaClassLoad(e)) {
                    log.debug("Retrying Java plugin {} with another candidate class name {}", plugin.getPluginId(), candidate.className());
                    continue;
                }
                throw e;
            }
        }

        IllegalStateException failure = new IllegalStateException(
                "No available candidate class names for Java plugin: " + plugin.getPluginId());
        for (Exception exception : failures) {
            failure.addSuppressed(exception);
        }
        throw failure;
    }

    private boolean isDuplicateJavaClassLoad(ResponseBusinessException exception) {
        String message = safeMessage(exception).toLowerCase();
        return message.contains("duplicate") || message.contains("linkageerror") || message.contains("attempted duplicate class definition");
    }

    private void recordDispatch(Long shellId, String pluginId, String action, Map<String, Object> args,
                                Map<String, Object> response, long start) {
        long durationMs = System.currentTimeMillis() - start;
        boolean success = response != null && isSuccess(response.get(Constants.CODE));
        String errorMsg = success || response == null ? null : String.valueOf(response.getOrDefault(Constants.ERROR, ""));
        shellOperationLogService.record(shellId, ShellOperationType.DISPATCH, pluginId, action,
                args, response, success, errorMsg, durationMs);
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
            case JAVA -> decodeBase64Payload(plugin, shellLanguage);
            case DOTNET -> validateDotNetAssemblyBytes(plugin, decodeBase64Payload(plugin, shellLanguage));
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

    private byte[] validateDotNetAssemblyBytes(Plugin plugin, byte[] payloadBytes) {
        if (payloadBytes.length < 2 || payloadBytes[0] != 'M' || payloadBytes[1] != 'Z') {
            throw new IllegalArgumentException("DOTNET plugin payload is not a valid assembly: " + plugin.getPluginId());
        }
        return payloadBytes;
    }
}
