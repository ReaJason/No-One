package com.reajason.noone.server.profile.dto;

import lombok.Data;

@Data
public class ProfileQueryRequest {
    private String name;
    private String protocolType;
    private String sortBy = "id";
    private String sortOrder = "desc";
    private int page = 0;
    private int pageSize = 10;
}
