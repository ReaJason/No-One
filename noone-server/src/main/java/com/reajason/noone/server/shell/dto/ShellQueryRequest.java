package com.reajason.noone.server.shell.dto;

import lombok.Data;

/**
 * Request DTO for querying shells with pagination
 *
 * @author ReaJason
 * @since 2025/12/27
 */
@Data
public class ShellQueryRequest {
    private String group;
    private String status;
    private Long projectId;
    private int page = 0;
    private int pageSize = 20;
    private String sortBy = "createTime";
    private String sortOrder = "desc";
}
