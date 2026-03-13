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
    private String bizName;
    private String description;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime archivedAt;
    private String remark;
    private Set<ProjectMemberDTO> members;
}
