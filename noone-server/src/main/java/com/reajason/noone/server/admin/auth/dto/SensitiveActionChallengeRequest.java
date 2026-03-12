package com.reajason.noone.server.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SensitiveActionChallengeRequest {
    @NotBlank
    private String verificationMethod;

    @NotBlank
    private String action;

    private String targetType;
    private String targetId;
    private String password;
    private String twoFactorCode;
}
