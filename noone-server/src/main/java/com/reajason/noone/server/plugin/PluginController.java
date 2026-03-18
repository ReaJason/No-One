package com.reajason.noone.server.plugin;

import tools.jackson.databind.ObjectMapper;
import com.reajason.noone.server.plugin.dto.PluginCreateRequest;
import com.reajason.noone.server.plugin.dto.PluginQueryRequest;
import com.reajason.noone.server.plugin.dto.PluginResponse;
import com.reajason.noone.server.plugin.dto.PluginUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/plugins")
@RequiredArgsConstructor
public class PluginController {

    private final PluginService pluginService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<PluginResponse> create(@Valid @RequestBody PluginCreateRequest request) {
        PluginResponse response = pluginService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<PluginResponse>> query(PluginQueryRequest request) {
        return ResponseEntity.ok(pluginService.query(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PluginResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(pluginService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PluginResponse> update(@PathVariable Long id, @Valid @RequestBody PluginUpdateRequest request) {
        return ResponseEntity.ok(pluginService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        pluginService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/upload")
    public ResponseEntity<PluginResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        PluginCreateRequest request = objectMapper.readValue(file.getInputStream(), PluginCreateRequest.class);
        PluginResponse response = pluginService.create(request, PluginSource.UPLOADED);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
