package com.reajason.noone.server.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForcePasswordChangeRequest {
    @NotBlank
    @Size(min = 6, max = 100)
    private String newPassword;
}
