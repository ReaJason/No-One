package com.reajason.noone.server.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SetupCodeRequest {
    @NotBlank(message = "Two factor code is required")
    private String twoFactorCode;

    @NotBlank(message = "New password is required")
    private String newPassword;
}
