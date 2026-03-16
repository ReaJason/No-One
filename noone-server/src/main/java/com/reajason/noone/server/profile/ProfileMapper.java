package com.reajason.noone.server.profile;

import com.reajason.noone.server.profile.config.HttpProtocolConfig;
import com.reajason.noone.server.profile.config.HttpProtocolConfigDefaults;
import com.reajason.noone.server.profile.config.ProtocolConfig;
import com.reajason.noone.server.profile.dto.ProfileCreateRequest;
import com.reajason.noone.server.profile.dto.ProfileResponse;
import com.reajason.noone.server.profile.dto.ProfileUpdateRequest;
import jakarta.annotation.Resource;
import org.mapstruct.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProfileMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Profile toEntity(ProfileCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(@MappingTarget Profile profile, ProfileUpdateRequest request);

    @Mapping(target = "id", source = "id")
    ProfileResponse toResponse(Profile profile);
}
