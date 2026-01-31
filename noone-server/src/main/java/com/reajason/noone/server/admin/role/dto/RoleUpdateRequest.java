package com.reajason.noone.server.admin.role.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class RoleUpdateRequest {
    @Size(min = 2, max = 50)
    private String name;

    private Set<Long> permissionIds;
}
