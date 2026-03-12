package com.reajason.noone.server.admin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginErrorResponse {
    private String code;
    private String message;
    private String status;
    private Boolean mfaRequired;
    private String setupToken;
    private String actionToken;

    public LoginErrorResponse(String code, String message, String status, Boolean mfaRequired) {
        this(code, message, status, mfaRequired, null, null);
    }

    public LoginErrorResponse(String code, String message, String status, Boolean mfaRequired, String setupToken) {
        this(code, message, status, mfaRequired, setupToken, null);
    }
}
