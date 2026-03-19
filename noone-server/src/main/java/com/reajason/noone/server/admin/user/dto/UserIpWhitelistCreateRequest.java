package com.reajason.noone.server.admin.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserIpWhitelistCreateRequest {
    @NotBlank
    private String ipAddress;

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("不支持的字段：" + name);
    }
}
