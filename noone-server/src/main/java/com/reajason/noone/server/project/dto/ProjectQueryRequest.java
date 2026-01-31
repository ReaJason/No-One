package com.reajason.noone.server.project.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectQueryRequest {
    private String name;
    private String code;
    private String status;
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private String sortBy = "createdAt";
    private String sortOrder = "desc";
    private int page = 0;
    private int pageSize = 10;
}


