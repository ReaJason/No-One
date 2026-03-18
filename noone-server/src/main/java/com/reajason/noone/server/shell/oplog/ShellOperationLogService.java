package com.reajason.noone.server.shell.oplog;

import com.reajason.noone.server.shell.oplog.dto.ShellOperationLogQueryRequest;
import com.reajason.noone.server.shell.oplog.dto.ShellOperationLogResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class ShellOperationLogService {

    private static final int MAX_RESULT_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> SKIP_RESULT_ACTIONS = Set.of("download");

    @Resource
    private ShellOperationLogRepository repository;

    @Resource
    private ShellOperationLogMapper mapper;

    @Resource
    private ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long shellId, ShellOperationType operation, String pluginId,
                       String action, Map<String, Object> args, Map<String, Object> result,
                       boolean success, String errorMessage, long durationMs) {
        try {
            String username = getCurrentUsername();

            ShellOperationLog opLog = new ShellOperationLog();
            opLog.setShellId(shellId);
            opLog.setUsername(username);
            opLog.setOperation(operation);
            opLog.setPluginId(pluginId);
            opLog.setAction(action);
            opLog.setArgs(args);
            opLog.setSuccess(success);
            opLog.setErrorMessage(truncate(errorMessage, 2000));
            opLog.setDurationMs(durationMs);

            if (shouldStoreResult(pluginId, action)) {
                opLog.setResult(truncateResult(result));
            }

            repository.save(opLog);
        } catch (Exception e) {
            log.warn("Failed to record shell operation log: shellId={}, operation={}", shellId, operation, e);
        }
    }

    @Transactional(readOnly = true)
    public Page<ShellOperationLogResponse> query(Long shellId, ShellOperationLogQueryRequest request) {
        String username = getCurrentUsername();

        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortOrder()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy());
        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        ShellOperationType operationType = null;
        if (request.getOperation() != null && !request.getOperation().isBlank()) {
            operationType = ShellOperationType.valueOf(request.getOperation().toUpperCase());
        }

        Specification<ShellOperationLog> spec = ShellOperationLogSpecifications.hasShellId(shellId)
                .and(ShellOperationLogSpecifications.hasUsername(username))
                .and(ShellOperationLogSpecifications.hasPluginId(request.getPluginId()))
                .and(ShellOperationLogSpecifications.hasOperation(operationType))
                .and(ShellOperationLogSpecifications.isSuccess(request.getSuccess()));

        return repository.findAll(spec, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<ShellOperationLogResponse> getLatestSuccessful(Long shellId, String pluginId) {
        String username = getCurrentUsername();
        return repository.findFirstByShellIdAndUsernameAndPluginIdAndSuccessTrueOrderByCreatedAtDesc(
                shellId, username, pluginId).map(mapper::toResponse);
    }

    private boolean shouldStoreResult(String pluginId, String action) {
        if (action != null
                && "file-manager".equals(pluginId)
                && SKIP_RESULT_ACTIONS.contains(action)) {
            return false;
        }
        return true;
    }

    private Map<String, Object> truncateResult(Map<String, Object> result) {
        if (result == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(result);
            if (json.length() <= MAX_RESULT_SIZE) {
                return result;
            }
            // Too large, store only metadata
            Map<String, Object> meta = new HashMap<>();
            if (result.containsKey("code")) {
                meta.put("code", result.get("code"));
            }
            if (result.containsKey("error")) {
                meta.put("error", result.get("error"));
            }
            meta.put("_truncated", true);
            meta.put("_originalSize", json.length());
            return meta;
        } catch (JacksonException e) {
            return null;
        }
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
