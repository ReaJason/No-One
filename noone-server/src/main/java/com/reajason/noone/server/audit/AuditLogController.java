package com.reajason.noone.server.audit;

import com.reajason.noone.server.audit.dto.AuditLogQueryRequest;
import com.reajason.noone.server.audit.dto.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("@authorizationService.hasSystemPermission('audit:log:read')")
    public ResponseEntity<Page<AuditLogResponse>> query(AuditLogQueryRequest request) {
        return ResponseEntity.ok(auditLogService.query(request));
    }
}
