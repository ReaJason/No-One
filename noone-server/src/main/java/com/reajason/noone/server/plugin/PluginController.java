package com.reajason.noone.server.plugin;

import com.reajason.noone.server.plugin.dto.PluginCreateRequest;
import com.reajason.noone.server.plugin.dto.PluginQueryRequest;
import com.reajason.noone.server.plugin.dto.PluginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plugins")
@RequiredArgsConstructor
public class PluginController {

    private final PluginService pluginService;

    @PostMapping
    public ResponseEntity<PluginResponse> create(@Valid @RequestBody PluginCreateRequest request) {
        PluginResponse response = pluginService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<PluginResponse>> query(PluginQueryRequest request) {
        return ResponseEntity.ok(pluginService.query(request));
    }
}
