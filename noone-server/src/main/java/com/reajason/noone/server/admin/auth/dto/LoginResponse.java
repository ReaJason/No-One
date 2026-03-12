package com.reajason.noone.server.admin.auth.dto;

import com.reajason.noone.server.admin.user.dto.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private String token;
    private String refreshToken;
    private UserResponse user;
    private Boolean mfaRequired;
    private Long expiresIn;
    private String sessionId;
    private String nextAction;
    private String actionToken;
}
