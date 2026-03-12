package com.reajason.noone.server.admin.user.dto;

import com.reajason.noone.server.admin.user.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;


@Data
public class UserQueryRequest {
    private String username;
    private Long roleId;
    private UserStatus status;
    /**
     * Backward-compatible query parameter.
     * true -> ENABLED, false -> DISABLED when status is not provided.
     */
    private Boolean enabled;
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private String sortBy = "createdAt";
    private String sortOrder = "desc";
    private int page = 0;
    private int pageSize = 10;
}
