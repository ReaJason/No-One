package com.reajason.noone.server.shell.oplog;

import com.reajason.noone.server.shell.oplog.dto.ShellOperationLogQueryRequest;
import com.reajason.noone.server.shell.oplog.dto.ShellOperationLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/shells/{shellId}/operations")
@RequiredArgsConstructor
public class ShellOperationLogController {

    private final ShellOperationLogService shellOperationLogService;

    @GetMapping
    public ResponseEntity<Page<ShellOperationLogResponse>> query(
            @PathVariable Long shellId,
            ShellOperationLogQueryRequest request) {
        return ResponseEntity.ok(shellOperationLogService.query(shellId, request));
    }

    @GetMapping("/latest")
    public ResponseEntity<ShellOperationLogResponse> getLatest(
            @PathVariable Long shellId,
            @RequestParam String pluginId) {
        Optional<ShellOperationLogResponse> latest = shellOperationLogService.getLatestSuccessful(shellId, pluginId);
        return latest.map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
