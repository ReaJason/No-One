package com.reajason.noone.server.admin.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class PermissionCreateRequest {
    @NotBlank(message = "权限代码不能为空")
    @Size(min = 2, max = 50, message = "权限代码长度必须在2-50个字符之间")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]*:[a-zA-Z][a-zA-Z0-9]*$", 
             message = "权限代码格式不正确，应为 'prefix:action' 格式，如 'user:create'")
    private String code;

    @NotBlank(message = "权限名称不能为空")
    @Size(min = 2, max = 100, message = "权限名称长度必须在2-100个字符之间")
    private String name;

    private Set<Long> roleIds;
}
