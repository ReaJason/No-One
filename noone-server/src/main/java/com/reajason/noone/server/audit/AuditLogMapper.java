package com.reajason.noone.server.audit;

import com.reajason.noone.server.audit.dto.AuditLogResponse;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogResponse toResponse(AuditLogEntity entity) {
        AuditLogResponse response = new AuditLogResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setUsername(entity.getUsername());
        response.setModule(entity.getModule().name());
        response.setAction(entity.getAction().name());
        response.setTargetType(entity.getTargetType());
        response.setTargetId(entity.getTargetId());
        response.setDescription(entity.getDescription());
        response.setSuccess(entity.isSuccess());
        response.setErrorMessage(entity.getErrorMessage());
        response.setDurationMs(entity.getDurationMs());
        response.setIpAddress(entity.getIpAddress());
        response.setUserAgent(entity.getUserAgent());
        response.setRequestMethod(entity.getRequestMethod());
        response.setRequestUri(entity.getRequestUri());
        response.setDetails(entity.getDetails());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
