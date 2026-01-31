package com.reajason.noone.server.project;

import com.reajason.noone.server.admin.user.UserRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectService {

    @Resource
    private ProjectRepository projectRepository;
    @Resource
    private UserRepository userRepository;
    @Resource
    private ProjectMapper projectMapper;

    public ProjectResponse create(ProjectCreateRequest request) {
        if (projectRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("项目名称已存在：" + request.getName());
        }
        if (projectRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("项目代号已存在：" + request.getCode());
        }
        Project project = projectMapper.toEntity(request);
        Project saved = projectRepository.save(project);
        return projectMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + id));
//        enforceReadPermission(project);
        return projectMapper.toResponse(project);
    }

    public ProjectResponse update(Long id, ProjectUpdateRequest request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + id));
//        enforceWritePermission(project);

        if (request.getName() != null && projectRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new IllegalArgumentException("项目名称已存在：" + request.getName());
        }
        if (request.getCode() != null && projectRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new IllegalArgumentException("项目代号已存在：" + request.getCode());
        }

        projectMapper.updateEntity(project, request);
        Project saved = projectRepository.save(project);
        return projectMapper.toResponse(saved);
    }

    public void delete(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + id));
//        enforceWritePermission(project);
        projectRepository.delete(project);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> query(ProjectQueryRequest request) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortOrder()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy()
        ).and(Sort.by(Sort.Direction.ASC, "name"));

        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        String currentUsername = authentication.getName();
//        User currentUser = userRepository.findByUsername(currentUsername).orElseThrow();
//        boolean isAdmin = authentication.getAuthorities().stream()
//                .anyMatch(a -> a.getAuthority().equals("admin") || a.getAuthority().equals("ROLE_ADMINISTRATOR"));

        Specification<Project> spec = ProjectSpecifications.hasName(request.getName())
                .or(ProjectSpecifications.hasCode(request.getName()))
                .and(ProjectSpecifications.hasStatus(parseStatus(request.getStatus())))
                .and(ProjectSpecifications.createdAfter(request.getCreatedAfter()))
                .and(ProjectSpecifications.createdBefore(request.getCreatedBefore()));

//        if (!isAdmin) {
//            spec = spec.and(ProjectSpecifications.isMember(currentUser));
//        }

        return projectRepository.findAll(spec, pageable).map(projectMapper::toResponse);
    }

    private ProjectStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return ProjectStatus.valueOf(status.toUpperCase());
    }

    private void enforceReadPermission(Project project) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("admin") || a.getAuthority().equals("ROLE_ADMINISTRATOR"));
        if (isAdmin) {
            return;
        }
        String currentUsername = authentication.getName();
        boolean isMember = project.getMembers().stream().anyMatch(u -> u.getUsername().equals(currentUsername));
        if (!isMember) {
            throw new IllegalArgumentException("无权访问该项目");
        }
    }

    private void enforceWritePermission(Project project) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("admin") || a.getAuthority().equals("ROLE_ADMINISTRATOR"));
        if (!isAdmin) {
            throw new IllegalArgumentException("只有管理员可以修改或删除项目");
        }
    }
}