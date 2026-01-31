package com.reajason.noone.server.admin.auth.dto;

import com.reajason.noone.server.admin.user.dto.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private UserResponse user;
}
