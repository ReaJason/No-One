package com.reajason.noone.server.shell;

import com.reajason.noone.Constants;
import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.exception.ResponseBusinessException;
import com.reajason.noone.core.exception.ShellRequestException;
import com.reajason.noone.core.exception.ShellResponseException;
import com.reajason.noone.server.plugin.BuiltinPluginRegistryService;
import com.reajason.noone.server.plugin.JavaPluginPayloadService;
import com.reajason.noone.server.plugin.Plugin;
import com.reajason.noone.server.plugin.PluginRepository;
import com.reajason.noone.server.shell.dto.ShellPluginStatusResponse;
import com.reajason.noone.server.shell.oplog.ShellOpLog;
import com.reajason.noone.server.shell.oplog.ShellOperationType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@Transactional
public class ShellPluginService {

    @Resource
    private ShellLookupHelper shellLookupHelper;
    @Resource
    private ShellConnectionPool shellConnectionPool;
    @Resource
    private ShellCoreInitHelper shellCoreInitHelper;
    @Resource
    private ShellPluginPayloadResolver shellPluginPayloadResolver;
    @Resource
    private ShellResponseHelper shellResponseHelper;
    @Resource
    private ShellStatusUpdater shellStatusUpdater;
    @Resource
    private JavaPluginPayloadService javaPluginPayloadService;
    @Resource
    private BuiltinPluginRegistryService builtinPluginRegistryService;
    @Resource
    private PluginRepository pluginRepository;
    @Resource
    private ShellRepository shellRepository;

    private static final String TASK_MANAGER_PLUGIN_ID = "task-manager";

    @ShellOpLog(operation = ShellOperationType.DISPATCH, shellId = "#shellId", pluginId = "#pluginId")
    public Map<String, Object> dispatchPlugin(Long shellId, String pluginId, Map<String, Object> args) {
        Shell shell = shellLookupHelper.requireById(shellId);
        ShellLanguage shellLanguage = shell.getLanguage() != null ? shell.getLanguage() : ShellLanguage.JAVA;
        Plugin plugin = findPlugin(pluginId, shellLanguage).orElse(null);
        String action = args != null ? (String) args.get("action") : null;
        if (action == null) {
            action = args != null ? ((String) args.get("op")) : null;
        }
        try {
            ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
            ensurePluginCacheSnapshot(connection, shellId);

            String runMode = resolveRunMode(plugin);
            boolean isTaskAction = action != null && action.startsWith("_task_");

            if (isTaskAction) {
                String taskOp = action.substring(6);
                return executeViaTaskManager(connection, shellLanguage, plugin, pluginId, taskOp, args, shellId);
            }

            if ("async".equals(runMode)) {
                return executeViaTaskManager(connection, shellLanguage, plugin, pluginId, "submit", args, shellId);
            }

            if ("scheduled".equals(runMode)) {
                return executeViaTaskManager(connection, shellLanguage, plugin, pluginId, "schedule", args, shellId);
            }

            ensurePluginLoaded(connection, plugin, shellLanguage, shellId);
            Map<String, Object> result = connection.runPlugin(pluginId, args);
            Map<String, Object> response = shellResponseHelper.handleShellConnectionResult(result);
            if (shellResponseHelper.isSuccess(response.get(Constants.CODE))) {
                shellStatusUpdater.markConnected(shellId);
                if ("system-info".equals(pluginId)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    shell.setBasicInfo(data);

                    String rawOsName = SystemInfoNormalizer.extractString(data, "os", "name");
                    String normalizedOs = SystemInfoNormalizer.normalizeOsName(rawOsName);
                    shell.setOs(normalizedOs);

                    String rawArch = SystemInfoNormalizer.extractString(data, "os", "arch");
                    shell.setArch(SystemInfoNormalizer.normalizeArch(rawArch, normalizedOs));

                    String runtimeType = SystemInfoNormalizer.extractString(data, "runtime", "type");
                    String runtimeVer = SystemInfoNormalizer.extractString(data, "runtime", "version");
                    if (runtimeType != null && runtimeVer != null) {
                        shell.setRuntimeVersion(runtimeType + " " + runtimeVer);
                    } else if (runtimeVer != null) {
                        shell.setRuntimeVersion(runtimeVer);
                    }

                    shellRepository.save(shell);
                }
            }

            return response;
        } catch (ShellRequestException e) {
            shellStatusUpdater.markError(shellId);
            return shellResponseHelper.failureResponse("Dispatch failed: " + shellResponseHelper.safeMessage(e), e);
        } catch (ShellResponseException e) {
            return shellResponseHelper.failureResponse("Dispatch failed: " + shellResponseHelper.safeMessage(e), e);
        } catch (Exception e) {
            return shellResponseHelper.failureResponse("Dispatch failed: " + shellResponseHelper.safeMessage(e), e);
        }
    }

    @Transactional(readOnly = true)
    public ShellPluginStatusResponse getPluginStatus(Long shellId, String pluginId) {
        Shell shell = shellLookupHelper.requireById(shellId);
        ShellLanguage shellLanguage = shell.getLanguage() != null ? shell.getLanguage() : ShellLanguage.JAVA;
        Plugin plugin = findPlugin(pluginId, shellLanguage)
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + pluginId));
        ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
        return toPluginStatus(plugin, connection);
    }

    public ShellPluginStatusResponse updatePlugin(Long shellId, String pluginId) {
        Shell shell = shellLookupHelper.requireById(shellId);
        ShellLanguage shellLanguage = shell.getLanguage() != null ? shell.getLanguage() : ShellLanguage.JAVA;
        Plugin plugin = findPlugin(pluginId, shellLanguage)
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + pluginId));
        ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
        loadPlugin(connection, plugin, shellLanguage, true, shellId);
        return toPluginStatus(plugin, connection);
    }

    @Transactional(readOnly = true)
    public Map<String, ShellPluginStatusResponse> getAllPluginStatuses(Long shellId) {
        Shell shell = shellLookupHelper.requireById(shellId);
        ShellLanguage shellLanguage = shell.getLanguage() != null ? shell.getLanguage() : ShellLanguage.JAVA;
        List<Plugin> plugins = pluginRepository.findAllByLanguage(shellLanguage.getValue());
        ShellConnection connection = shellConnectionPool.getOrCreateCached(shell);
        Map<String, ShellPluginStatusResponse> result = new LinkedHashMap<>();
        for (Plugin plugin : plugins) {
            result.put(plugin.getPluginId(), toPluginStatus(plugin, connection));
        }
        return result;
    }

    @ShellOpLog(operation = ShellOperationType.LOAD_PLUGIN, shellId = "#shellId", pluginId = "#plugin.pluginId",
            action = "#forceRefresh ? 'refresh' : 'load'")
    public void loadPlugin(ShellConnection connection, Plugin plugin, ShellLanguage shellLanguage,
                           boolean forceRefresh, Long shellId) {
        doLoadPlugin(connection, plugin, shellLanguage, forceRefresh);
    }

    private Map<String, Object> executeViaTaskManager(ShellConnection connection, ShellLanguage shellLanguage,
                                                      Plugin plugin, String pluginId, String taskOp,
                                                      Map<String, Object> originalArgs,
                                                      Long shellId) {
        ensureInfrastructurePluginLoaded(connection, TASK_MANAGER_PLUGIN_ID, shellLanguage, shellId);
        boolean requiresTargetPlugin = "submit".equals(taskOp) || "schedule".equals(taskOp);
        if (requiresTargetPlugin) {
            ensurePluginLoaded(connection, plugin, shellLanguage, shellId);
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
        Map<String, Object> response = shellResponseHelper.handleShellConnectionResult(result);

        if (shellResponseHelper.isSuccess(response.get(Constants.CODE))) {
            shellStatusUpdater.markConnected(shellId);
        }

        return response;
    }

    private void ensurePluginLoaded(ShellConnection connection, Plugin plugin, ShellLanguage shellLanguage, Long shellId) {
        if (plugin != null && connection.needLoadPlugin(plugin.getPluginId())) {
            loadPlugin(connection, plugin, shellLanguage, false, shellId);
        }
    }

    private void ensureInfrastructurePluginLoaded(ShellConnection connection, String pluginId,
                                                  ShellLanguage shellLanguage, Long shellId) {
        Optional<Plugin> pluginOptional = findPlugin(pluginId, shellLanguage);
        if (pluginOptional.isEmpty()) {
            return;
        }
        Plugin plugin = pluginOptional.get();
        if (connection.needLoadPlugin(pluginId)) {
            loadPlugin(connection, plugin, shellLanguage, false, shellId);
            return;
        }
        if (connection.isPluginOutdated(pluginId, plugin.getVersion())) {
            loadPlugin(connection, plugin, shellLanguage, true, shellId);
        }
    }

    private void ensurePluginCacheSnapshot(ShellConnection connection, Long shellId) {
        if (!connection.isPluginCacheInitialized()) {
            shellCoreInitHelper.initCoreIfNeeded(connection, shellId);
            connection.test();
        }
    }

    private void doLoadPlugin(ShellConnection connection, Plugin plugin, ShellLanguage shellLanguage, boolean forceRefresh) {
        byte[] payloadBytes = shellPluginPayloadResolver.resolve(shellLanguage, plugin);
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
                    log.debug("Retrying Java plugin {} with another candidate class name {}",
                            plugin.getPluginId(), candidate.className());
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
        String message = shellResponseHelper.safeMessage(exception).toLowerCase();
        return message.contains("duplicate") || message.contains("linkageerror")
                || message.contains("attempted duplicate class definition");
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
        boolean loaded = shellVersion != null || !connection.needLoadPlugin(plugin.getPluginId());
        boolean needsUpdate = shellVersion != null && compareVersions(plugin.getVersion(), shellVersion) > 0;
        return ShellPluginStatusResponse.builder()
                .pluginId(plugin.getPluginId())
                .serverVersion(plugin.getVersion())
                .shellVersion(shellVersion)
                .loaded(loaded)
                .needsUpdate(needsUpdate)
                .build();
    }

    static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[^A-Za-z0-9]+");
        String[] rightParts = right.split("[^A-Za-z0-9]+");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            String leftPart = i < leftParts.length ? leftParts[i] : "0";
            String rightPart = i < rightParts.length ? rightParts[i] : "0";
            boolean leftNumeric = leftPart.chars().allMatch(Character::isDigit);
            boolean rightNumeric = rightPart.chars().allMatch(Character::isDigit);
            int cmp;
            if (leftNumeric && rightNumeric) {
                cmp = Integer.compare(Integer.parseInt(leftPart), Integer.parseInt(rightPart));
            } else if (leftNumeric != rightNumeric) {
                cmp = leftNumeric ? 1 : -1;
            } else {
                cmp = leftPart.compareToIgnoreCase(rightPart);
            }
            if (cmp != 0) return cmp;
        }
        return 0;
    }
}
