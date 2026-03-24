package com.reajason.noone.server.profile;


import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.server.profile.dto.ProfileCreateRequest;
import com.reajason.noone.server.profile.dto.ProfileResponse;
import com.reajason.noone.server.profile.dto.ProfileUpdateRequest;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProfileMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    ProfileEntity toEntity(ProfileCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(@MappingTarget ProfileEntity profile, ProfileUpdateRequest request);

    @Mapping(target = "id", source = "id")
    ProfileResponse toResponse(ProfileEntity profile);

    Profile toProfile(ProfileEntity profile);
}
