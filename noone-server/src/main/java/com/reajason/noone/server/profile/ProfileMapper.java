package com.reajason.noone.server.profile;

import com.reajason.noone.server.profile.config.HttpProtocolConfig;
import com.reajason.noone.server.profile.config.HttpProtocolConfigDefaults;
import com.reajason.noone.server.profile.config.ProtocolConfig;
import com.reajason.noone.server.profile.dto.ProfileCreateRequest;
import com.reajason.noone.server.profile.dto.ProfileResponse;
import com.reajason.noone.server.profile.dto.ProfileUpdateRequest;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class ProfileMapper {

    @Resource
    private PasswordEncoder passwordEncoder;

    public Profile toEntity(ProfileCreateRequest request) {
        Profile profile = new Profile();
        profile.setName(request.getName());
        profile.setPassword(passwordEncoder.encode(request.getPassword()));
        profile.setProtocolType(request.getProtocolType());
        profile.setIdentifier(request.getIdentifier());
        ProtocolConfig protocolConfig = request.getProtocolConfig();
        applyProtocolDefaults(protocolConfig);
        profile.setProtocolConfig(protocolConfig);
        profile.setRequestTransformations(request.getRequestTransformations());
        profile.setResponseTransformations(request.getResponseTransformations());
        return profile;
    }

    public void updateEntity(Profile profile, ProfileUpdateRequest request) {
        if (request.getName() != null && !request.getName().isBlank()) {
            profile.setName(request.getName());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            profile.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getProtocolType() != null) {
            profile.setProtocolType(request.getProtocolType());
        }
        if (request.getIdentifier() != null) {
            profile.setIdentifier(request.getIdentifier());
        }
        if (request.getProtocolConfig() != null) {
            ProtocolConfig protocolConfig = request.getProtocolConfig();
            applyProtocolDefaults(protocolConfig);
            profile.setProtocolConfig(protocolConfig);
        }
        if (request.getRequestTransformations() != null) {
            profile.setRequestTransformations(request.getRequestTransformations());
        }
        if (request.getResponseTransformations() != null) {
            profile.setResponseTransformations(request.getResponseTransformations());
        }
    }

    public ProfileResponse toResponse(Profile profile) {
        ProfileResponse response = new ProfileResponse();
        response.setId(profile.getId());
        response.setName(profile.getName());
        response.setProtocolType(profile.getProtocolType());
        response.setIdentifier(profile.getIdentifier());
        response.setProtocolConfig(profile.getProtocolConfig());
        response.setRequestTransformations(profile.getRequestTransformations());
        response.setResponseTransformations(profile.getResponseTransformations());
        response.setCreatedAt(profile.getCreatedAt());
        response.setUpdatedAt(profile.getUpdatedAt());
        return response;
    }

    private void applyProtocolDefaults(ProtocolConfig protocolConfig) {
        if (protocolConfig instanceof HttpProtocolConfig httpProtocolConfig) {
            HttpProtocolConfigDefaults.applyDefaults(httpProtocolConfig);
        }
    }
}
