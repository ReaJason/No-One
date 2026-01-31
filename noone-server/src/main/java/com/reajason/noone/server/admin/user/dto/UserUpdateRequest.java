package com.reajason.noone.server.admin.user.dto;

import lombok.Data;

import java.util.Set;

@Data
public class UserUpdateRequest {
    private Boolean enabled;
    private Set<Long> roleIds;
}
