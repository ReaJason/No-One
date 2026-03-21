package com.reajason.noone.server.shell.oplog;

import com.reajason.noone.server.shell.oplog.dto.ShellOperationLogQueryRequest;
import com.reajason.noone.server.shell.oplog.dto.ShellOperationLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shell-operations")
@RequiredArgsConstructor
public class GlobalShellOperationLogController {

    private final ShellOperationLogService shellOperationLogService;

    @GetMapping
    @PreAuthorize("@authorizationService.hasSystemPermission('shell:operation:read')")
    public ResponseEntity<Page<ShellOperationLogResponse>> query(ShellOperationLogQueryRequest request) {
        return ResponseEntity.ok(shellOperationLogService.queryAll(request));
    }
}
