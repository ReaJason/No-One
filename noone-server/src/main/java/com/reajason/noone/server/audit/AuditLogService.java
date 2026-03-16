package com.reajason.noone.server.audit;

import com.reajason.noone.server.audit.dto.AuditLogQueryRequest;
import com.reajason.noone.server.audit.dto.AuditLogResponse;
import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.Getter;
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

import java.util.Map;

@Slf4j
@Service
public class AuditLogService {

    @Resource
    private AuditLogRepository auditLogRepository;

    @Resource
    private RequestContext requestContext;

    @Resource
    private AuditLogMapper auditLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEntry entry) {
        try {
            AuditLogEntity entity = new AuditLogEntity();
            entity.setModule(entry.getModule());
            entity.setAction(entry.getAction());
            entity.setTargetType(entry.getTargetType());
            entity.setTargetId(entry.getTargetId());
            entity.setDescription(entry.getDescription());
            entity.setSuccess(entry.isSuccess());
            entity.setErrorMessage(entry.getErrorMessage());
            entity.setDurationMs(entry.getDurationMs());

            // User info: use overrides or resolve from SecurityContext
            if (entry.getUserId() != null) {
                entity.setUserId(entry.getUserId());
            }
            entity.setUsername(entry.getUsername() != null ? entry.getUsername() : getCurrentUsername());

            // HTTP context: use overrides or resolve from RequestContext
            entity.setIpAddress(entry.getIpAddress() != null ? entry.getIpAddress() : safeGetRequestContext(requestContext::getIpAddress));
            entity.setUserAgent(entry.getUserAgent() != null ? entry.getUserAgent() : safeGetRequestContext(requestContext::getUserAgent));
            entity.setRequestMethod(safeGetRequestContext(requestContext::getRequestMethod));
            entity.setRequestUri(safeGetRequestContext(requestContext::getRequestUri));

            // Details
            entity.setDetails(entry.getDetails());

            auditLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to record audit log: module={}, action={}", entry.getModule(), entry.getAction(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> query(AuditLogQueryRequest request) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortOrder()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy());
        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        Specification<AuditLogEntity> spec = AuditLogSpecifications.hasModule(request.getModule())
                .and(AuditLogSpecifications.hasAction(request.getAction()))
                .and(AuditLogSpecifications.hasUsername(request.getUsername()))
                .and(AuditLogSpecifications.hasTargetType(request.getTargetType()))
                .and(AuditLogSpecifications.hasTargetId(request.getTargetId()))
                .and(AuditLogSpecifications.isSuccess(request.getSuccess()))
                .and(AuditLogSpecifications.createdAfter(request.getCreatedAfter()))
                .and(AuditLogSpecifications.createdBefore(request.getCreatedBefore()));

        return auditLogRepository.findAll(spec, pageable).map(auditLogMapper::toResponse);
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private String safeGetRequestContext(java.util.function.Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return null;
        }
    }

    @Getter
    @Builder
    public static class AuditEntry {
        private final AuditModule module;
        private final AuditAction action;
        private final String targetType;
        private final String targetId;
        private final String description;
        @Builder.Default
        private final boolean success = true;
        private final String errorMessage;
        private final Long durationMs;
        // Optional overrides (for unauthenticated flows like login)
        private final Long userId;
        private final String username;
        private final String ipAddress;
        private final String userAgent;
        private final Map<String, Object> details;
    }
}
