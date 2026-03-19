package com.reajason.noone.server.admin.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserSessionQueryRequest {
    private Boolean revoked;
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private int page = 0;
    private int pageSize = 20;
}
