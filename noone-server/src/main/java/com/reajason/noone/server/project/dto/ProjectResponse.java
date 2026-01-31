package com.reajason.noone.server.project.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class ProjectResponse {
    private Long id;
    private String name;
    private String code;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<ProjectMemberDTO> members;
}


