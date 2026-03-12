package com.reajason.noone.server.admin.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    /**
     * Backward-compatible field. Admin reset flow does not require old password.
     */
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度必须在6-20个字符之间")
    private String newPassword;

    /**
     * Reserved for future enforcement. Currently accepted and ignored.
     */
    private Boolean forceChangeOnNextLogin = true;
}
