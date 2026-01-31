package com.reajason.noone.server.admin.role.dto;

import lombok.Data;

@Data
public class RoleQueryRequest {
    private String name;
    private int page = 0;
    private int pageSize = 10;
    private String sortBy = "createdAt";
    private String sortOrder = "desc";
}
