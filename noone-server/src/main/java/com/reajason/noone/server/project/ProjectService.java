package com.reajason.noone.server.project;

import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.api.ResourceNotFoundException;
import com.reajason.noone.server.audit.AuditAction;
import com.reajason.noone.server.audit.AuditLog;
import com.reajason.noone.server.audit.AuditModule;
import com.reajason.noone.server.config.AuthorizationService;
import com.reajason.noone.server.project.dto.ProjectCreateRequest;
import com.reajason.noone.server.project.dto.ProjectQueryRequest;
import com.reajason.noone.server.project.dto.ProjectResponse;
import com.reajason.noone.server.project.dto.ProjectUpdateRequest;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectService {

    @Resource
    private ProjectRepository projectRepository;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private UserRepository userRepository;
    @Resource
    private AuthorizationService authorizationService;

    @AuditLog(module = AuditModule.PROJECT, action = AuditAction.CREATE, targetType = "Project", targetId = "#result.id")
    public ProjectResponse create(ProjectCreateRequest request) {
        if (projectRepository.existsByNameAndDeletedFalse(request.getName())) {
            throw new IllegalArgumentException("Project name already exists：" + request.getName());
        }
        if (projectRepository.existsByCodeAndDeletedFalse(request.getCode())) {
            throw new IllegalArgumentException("Project code already exists：" + request.getCode());
        }

        Project project = projectMapper.toEntity(request);
        Set<User> members = toMembers(request.getMemberIds());
        if (members != null) {
            project.setMembers(members);
        }
        syncArchivedAt(project);
        Project saved = projectRepository.save(project);
        return projectMapper.toResponse(projectRepository.findByIdAndDeletedFalse(saved.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long id) {
        Project project = projectRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found：" + id));
        return projectMapper.toResponse(project);
    }

    @AuditLog(module = AuditModule.PROJECT, action = AuditAction.UPDATE, targetType = "Project", targetId = "#id")
    public ProjectResponse update(Long id, ProjectUpdateRequest request) {
        Project project = projectRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found：" + id));

        if (request.getName() != null && projectRepository.existsByNameAndIdNotAndDeletedFalse(request.getName(), id)) {
            throw new IllegalArgumentException("Project name already exists：" + request.getName());
        }
        if (request.getCode() != null && projectRepository.existsByCodeAndIdNotAndDeletedFalse(request.getCode(), id)) {
            throw new IllegalArgumentException("Project code already exists：" + request.getCode());
        }

        projectMapper.updateEntity(project, request);
        Set<User> members = toMembers(request.getMemberIds());
        if (members != null) {
            project.setMembers(members);
        }
        syncArchivedAt(project);
        Project saved = projectRepository.save(project);
        return projectMapper.toResponse(projectRepository.findByIdAndDeletedFalse(saved.getId()).orElseThrow());
    }

    @AuditLog(module = AuditModule.PROJECT, action = AuditAction.DELETE, targetType = "Project", targetId = "#id")
    public void delete(Long id) {
        Project project = projectRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found：" + id));
        project.setDeleted(Boolean.TRUE);
        projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> query(ProjectQueryRequest request) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortOrder()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy()
        ).and(Sort.by(Sort.Direction.ASC, "name"));

        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        Specification<Project> spec = ProjectSpecifications.notDeleted()
                .and(ProjectSpecifications.hasName(request.getName()))
                .and(ProjectSpecifications.hasCode(request.getCode()))
                .and(ProjectSpecifications.hasStatus(parseStatus(request.getStatus())))
                .and(ProjectSpecifications.createdAfter(request.getCreatedAfter()))
                .and(ProjectSpecifications.createdBefore(request.getCreatedBefore()));

        Set<Long> visibleProjectIds = authorizationService.getVisibleProjectIds();
        if (!visibleProjectIds.isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("id").in(visibleProjectIds));
        } else if (!authorizationService.isAdmin()) {
            spec = spec.and((root, query, cb) -> cb.disjunction());
        }

        return projectRepository.findAll(spec, pageable).map(projectMapper::toResponse);
    }
    private ProjectStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return ProjectStatus.valueOf(status.toUpperCase());
    }

    private void syncArchivedAt(Project project) {
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            if (project.getArchivedAt() == null) {
                project.setArchivedAt(LocalDateTime.now());
            }
            return;
        }
        project.setArchivedAt(null);
    }

    private Set<User> toMembers(Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return null;
        }
        return memberIds.stream()
                .map(userRepository::getReferenceById)
                .collect(Collectors.toSet());
    }
}
