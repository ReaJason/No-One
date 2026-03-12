package com.reajason.noone.server.admin.auth.dto;

import com.reajason.noone.server.admin.user.dto.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthMeResponse {
    private UserResponse user;
    private boolean authenticated;
}
