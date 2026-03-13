package com.reajason.noone.server.plugin.dto;

import lombok.Data;

@Data
public class PluginQueryRequest {
    private String name;
    private String language;
    private String type;
    private String sortBy = "createdAt";
    private String sortOrder = "desc";
    private int page = 0;
    private int pageSize = 10;
}
