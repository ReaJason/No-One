package com.reajason.noone.server.profile.dto;

import com.reajason.noone.server.profile.config.IdentifierConfig;
import com.reajason.noone.server.profile.config.ProtocolConfig;
import com.reajason.noone.server.profile.config.ProtocolType;
import lombok.Data;

import java.util.List;

@Data
public class ProfileUpdateRequest {
    private String name;
    private String password;
    private ProtocolType protocolType;
    private IdentifierConfig identifier;
    private ProtocolConfig protocolConfig;
    private List<String> requestTransformations;
    private List<String> responseTransformations;
}
