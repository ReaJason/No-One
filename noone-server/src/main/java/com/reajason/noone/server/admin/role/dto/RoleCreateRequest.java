package com.reajason.noone.server.admin.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class RoleCreateRequest {
    @NotBlank
    @Size(min = 2, max = 50)
    private String name;

    private Set<Long> permissionIds;
}
