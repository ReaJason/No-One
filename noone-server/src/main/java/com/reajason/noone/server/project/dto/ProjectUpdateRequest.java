package com.reajason.noone.server.project.dto;

import lombok.Data;

import java.util.Set;

@Data
public class ProjectUpdateRequest {
    private String name;
    private String code;
    private String status;
    private Set<Long> memberIds;
}


