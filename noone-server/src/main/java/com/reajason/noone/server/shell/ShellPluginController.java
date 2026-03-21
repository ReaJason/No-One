package com.reajason.noone.server.shell;

import com.reajason.noone.server.shell.dto.ShellPluginDispatchRequest;
import com.reajason.noone.server.shell.dto.ShellPluginStatusResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/shells")
@RequiredArgsConstructor
public class ShellPluginController {

    private final ShellPluginService shellPluginService;

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("@authorizationService.hasSystemPermission('shell:dispatch')")
    public ResponseEntity<Map<String, Object>> dispatch(@PathVariable Long id,
                                                         @RequestBody ShellPluginDispatchRequest request) {
        Map<String, Object> args = request.getArgs();
        if (StringUtils.isNotBlank(request.getAction())) {
            args.put("action", request.getAction());
        }
        return ResponseEntity.ok(shellPluginService.dispatchPlugin(id, request.getPluginId(), args));
    }

    @GetMapping("/{id}/plugins/statuses")
    @PreAuthorize("@authorizationService.hasSystemPermission('shell:dispatch')")
    public ResponseEntity<Map<String, ShellPluginStatusResponse>> getAllPluginStatuses(@PathVariable Long id) {
        return ResponseEntity.ok(shellPluginService.getAllPluginStatuses(id));
    }

    @GetMapping("/{id}/plugins/{pluginId}/status")
    @PreAuthorize("@authorizationService.hasSystemPermission('shell:dispatch')")
    public ResponseEntity<ShellPluginStatusResponse> getPluginStatus(@PathVariable Long id,
                                                                      @PathVariable String pluginId) {
        return ResponseEntity.ok(shellPluginService.getPluginStatus(id, pluginId));
    }

    @PostMapping("/{id}/plugins/{pluginId}/update")
    @PreAuthorize("@authorizationService.hasSystemPermission('shell:dispatch')")
    public ResponseEntity<ShellPluginStatusResponse> updatePlugin(@PathVariable Long id,
                                                                    @PathVariable String pluginId) {
        return ResponseEntity.ok(shellPluginService.updatePlugin(id, pluginId));
    }
}
