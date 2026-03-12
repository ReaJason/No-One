package com.reajason.noone.server.project;

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

import java.util.Set;

@Service
@Transactional
public class ProjectService {

    @Resource
    private ProjectRepository projectRepository;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private AuthorizationService authorizationService;

    public ProjectResponse create(ProjectCreateRequest request) {
        if (projectRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("项目名称已存在：" + request.getName());
        }
        if (projectRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("项目代号已存在：" + request.getCode());
        }

        Project project = projectMapper.toEntity(request);
        if (authorizationService.isAuthenticated()) {
            project.setOwner(authorizationService.getCurrentUser());
        }
        Project saved = projectRepository.save(project);
        return projectMapper.toResponse(projectRepository.findById(saved.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + id));
        return projectMapper.toResponse(project);
    }

    public ProjectResponse update(Long id, ProjectUpdateRequest request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + id));

        if (request.getName() != null && projectRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new IllegalArgumentException("项目名称已存在：" + request.getName());
        }
        if (request.getCode() != null && projectRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new IllegalArgumentException("项目代号已存在：" + request.getCode());
        }

        projectMapper.updateEntity(project, request);
        Project saved = projectRepository.save(project);
        return projectMapper.toResponse(projectRepository.findById(saved.getId()).orElseThrow());
    }

    public void delete(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + id));
        projectRepository.delete(project);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> query(ProjectQueryRequest request) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortOrder()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy()
        ).and(Sort.by(Sort.Direction.ASC, "name"));

        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        Specification<Project> spec = ProjectSpecifications.hasName(request.getName())
                .or(ProjectSpecifications.hasCode(request.getName()))
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
}
