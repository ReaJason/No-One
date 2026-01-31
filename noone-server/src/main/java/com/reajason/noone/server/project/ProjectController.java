package com.reajason.noone.server.project;

import com.reajason.noone.server.project.dto.ProjectCreateRequest;
import com.reajason.noone.server.project.dto.ProjectQueryRequest;
import com.reajason.noone.server.project.dto.ProjectResponse;
import com.reajason.noone.server.project.dto.ProjectUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
//    @PreAuthorize("hasAuthority('project:create')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request) {
        log.info("Creating project: {}", request.getName());
        ProjectResponse response = projectService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
//    @PreAuthorize("hasAuthority('project:read')")
    public ResponseEntity<ProjectResponse> getById(@PathVariable Long id) {
        log.info("Getting project by id: {}", id);
        return ResponseEntity.ok(projectService.getById(id));
    }

    @PutMapping("/{id}")
//    @PreAuthorize("hasAuthority('project:update')")
    public ResponseEntity<ProjectResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProjectUpdateRequest request) {
        log.info("Updating project id: {}", id);
        ProjectResponse response = projectService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
//    @PreAuthorize("hasAuthority('project:delete')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting project id: {}", id);
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
//    @PreAuthorize("hasAuthority('project:list')")
    public ResponseEntity<Page<ProjectResponse>> query(ProjectQueryRequest request) {
        return ResponseEntity.ok(projectService.query(request));
    }
}


