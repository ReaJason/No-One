package com.reajason.noone.server.project.dto;

import com.reajason.noone.server.project.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class ProjectCreateRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String code;
    private ProjectStatus status;
    private String bizName;
    private String description;
    private Set<Long> memberIds;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String remark;
}
