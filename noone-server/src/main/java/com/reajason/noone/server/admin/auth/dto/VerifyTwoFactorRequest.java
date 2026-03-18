package com.reajason.noone.server.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyTwoFactorRequest {
    @NotBlank(message = "Action token is required")
    private String actionToken;

    @NotBlank(message = "Two-factor code is required")
    private String twoFactorCode;
}
