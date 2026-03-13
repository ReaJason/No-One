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
        uses = {ProfileMapper.PasswordEncoderMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProfileMapper {

    @Mapping(target = "password", source = "password", qualifiedByName = "encodePassword")
    @Mapping(target = "protocolConfig", source = "protocolConfig", qualifiedByName = "applyProtocolDefaults")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Profile toEntity(ProfileCreateRequest request);

    @Mapping(target = "password", source = "password", qualifiedByName = "encodePassword")
    @Mapping(target = "protocolConfig", source = "protocolConfig", qualifiedByName = "applyProtocolDefaults")
    void updateEntity(@MappingTarget Profile profile, ProfileUpdateRequest request);

    @Mapping(target = "id", source = "id")
    ProfileResponse toResponse(Profile profile);

    @Component
    class PasswordEncoderMapper {

        @Resource
        private PasswordEncoder passwordEncoder;

        @Named("encodePassword")
        public String encodePassword(String password) {
            if (password == null || password.isBlank()) {
                return null; // IGNORE 策略会跳过 null，不会覆盖原值
            }
            return passwordEncoder.encode(password);
        }

        @Named("applyProtocolDefaults")
        public ProtocolConfig applyProtocolDefaults(ProtocolConfig protocolConfig) {
            if (protocolConfig == null) return null;
            if (protocolConfig instanceof HttpProtocolConfig httpProtocolConfig) {
                HttpProtocolConfigDefaults.applyDefaults(httpProtocolConfig);
            }
            return protocolConfig;
        }
    }
}
