package com.reajason.noone.server.audit.dto;

import com.reajason.noone.server.audit.AuditAction;
import com.reajason.noone.server.audit.AuditModule;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogQueryRequest {
    private AuditModule module;
    private AuditAction action;
    private String username;
    private String targetType;
    private String targetId;
    private Boolean success;
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private int page = 0;
    private int pageSize = 20;
    private String sortBy = "createdAt";
    private String sortOrder = "desc";
}
