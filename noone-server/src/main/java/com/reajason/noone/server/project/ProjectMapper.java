package com.reajason.noone.server.project;

import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.project.dto.ProjectCreateRequest;
import com.reajason.noone.server.project.dto.ProjectMemberDTO;
import com.reajason.noone.server.project.dto.ProjectResponse;
import com.reajason.noone.server.project.dto.ProjectUpdateRequest;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ProjectMapper {

    @Resource
    private UserRepository userRepository;

    public Project toEntity(ProjectCreateRequest request) {
        Project project = new Project();
        project.setName(request.getName());
        project.setCode(request.getCode());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            project.setStatus(ProjectStatus.valueOf(request.getStatus().toUpperCase()));
        }
        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
            project.setMembers(request.getMemberIds().stream()
                    .map(userRepository::getReferenceById)
                    .collect(Collectors.toSet()));
        }
        return project;
    }

    public void updateEntity(Project project, ProjectUpdateRequest request) {
        if (request.getName() != null && !request.getName().isBlank()) {
            project.setName(request.getName());
        }
        if (request.getCode() != null && !request.getCode().isBlank()) {
            project.setCode(request.getCode());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            project.setStatus(ProjectStatus.valueOf(request.getStatus().toUpperCase()));
        }
        if (request.getMemberIds() != null) {
            project.setMembers(request.getMemberIds().stream()
                    .map(userRepository::getReferenceById)
                    .collect(Collectors.toSet()));
        }
    }

    public ProjectResponse toResponse(Project project) {
        ProjectResponse response = new ProjectResponse();
        response.setId(project.getId());
        response.setName(project.getName());
        response.setCode(project.getCode());
        response.setStatus(project.getStatus().name());
        response.setCreatedAt(project.getCreatedAt());
        response.setUpdatedAt(project.getUpdatedAt());
        response.setMembers(project.getMembers().stream().map(this::toMemberDTO).collect(Collectors.toSet()));
        return response;
    }

    private ProjectMemberDTO toMemberDTO(User user) {
        ProjectMemberDTO dto = new ProjectMemberDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        return dto;
    }
}


