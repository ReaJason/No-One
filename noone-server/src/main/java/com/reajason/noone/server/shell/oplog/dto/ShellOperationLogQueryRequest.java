package com.reajason.noone.server.shell.oplog.dto;

import lombok.Data;

@Data
public class ShellOperationLogQueryRequest {
    private String pluginId;
    private String operation;
    private Boolean success;
    private int page = 0;
    private int pageSize = 20;
    private String sortBy = "createdAt";
    private String sortOrder = "desc";
}
