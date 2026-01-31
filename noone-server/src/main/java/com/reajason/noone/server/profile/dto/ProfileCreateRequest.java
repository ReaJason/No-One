package com.reajason.noone.server.profile.dto;

import com.reajason.noone.server.profile.config.IdentifierConfig;
import com.reajason.noone.server.profile.config.ProtocolConfig;
import com.reajason.noone.server.profile.config.ProtocolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ProfileCreateRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String password;

    @NotNull
    private ProtocolType protocolType;

    private IdentifierConfig identifier;

    @NotNull
    private ProtocolConfig protocolConfig;

    private List<String> requestTransformations;

    private List<String> responseTransformations;
}
