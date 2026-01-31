package com.reajason.noone.server.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class ProjectCreateRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String code;
    private String status;
    private Set<Long> memberIds;
}


