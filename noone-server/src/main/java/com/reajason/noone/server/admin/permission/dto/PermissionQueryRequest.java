package com.reajason.noone.server.admin.permission.dto;

import lombok.Data;

@Data
public class PermissionQueryRequest {
    private String name;
    private Long roleId;
    private int page = 0;
    private int pageSize = 10;
    private String sortBy = "createdAt";
    private String sortDirection = "desc";
}
