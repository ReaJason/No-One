package com.reajason.noone.server.admin.user.dto;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class UserQueryRequest {
    private String username;
    private Long roleId;
    private Boolean enabled;
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private String sortBy = "createdAt";
    private String sortOrder = "desc";
    private int page = 0;
    private int pageSize = 10;
}
