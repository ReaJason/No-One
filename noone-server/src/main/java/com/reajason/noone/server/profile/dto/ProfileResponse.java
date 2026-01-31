package com.reajason.noone.server.profile.dto;

import com.reajason.noone.server.profile.config.IdentifierConfig;
import com.reajason.noone.server.profile.config.ProtocolConfig;
import com.reajason.noone.server.profile.config.ProtocolType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProfileResponse {
    private Long id;
    private String name;
    private ProtocolType protocolType;
    private IdentifierConfig identifier;
    private ProtocolConfig protocolConfig;
    private List<String> requestTransformations;
    private List<String> responseTransformations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
