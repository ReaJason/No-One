package com.reajason.noone.server.admin.user.dto;

import com.reajason.noone.server.admin.user.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class UserUpdateRequest {
    @Email
    @Size(max = 100)
    private String email;

    private UserStatus status;
    private Set<Long> roleIds;
}
