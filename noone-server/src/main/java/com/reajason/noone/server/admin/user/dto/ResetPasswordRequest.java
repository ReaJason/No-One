package com.reajason.noone.server.admin.user.dto;

import com.reajason.noone.server.admin.user.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    /**
     * Backward-compatible field. Admin reset flow does not require old password.
     */
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @ValidPassword
    private String newPassword;

    /**
     * Controls whether the user must change the password on the next login.
     */
    private Boolean forceChangeOnNextLogin = true;
}
