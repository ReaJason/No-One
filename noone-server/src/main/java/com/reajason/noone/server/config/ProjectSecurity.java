package com.reajason.noone.server.config;

import com.reajason.noone.server.project.Project;
import com.reajason.noone.server.project.ProjectRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("projectSecurity")
public class ProjectSecurity {

    private final ProjectRepository projectRepository;

    public ProjectSecurity(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public boolean isMember(Authentication authentication, Long projectId) {
        String username = authentication.getName();
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return false;
        }
        return project.getMembers().stream()
                .anyMatch(user -> user.getUsername().equals(username));
    }
}