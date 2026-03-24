package com.reajason.noone.core.profile;

import com.reajason.noone.core.profile.config.IdentifierConfig;
import com.reajason.noone.core.profile.config.ProtocolConfig;
import com.reajason.noone.core.profile.config.ProtocolType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Profile entity.
 *
 * @author ReaJason
 * @since 2025/9/23
 */
@Data
public class Profile {
    private Long id;
    private String name;
    private ProtocolType protocolType = ProtocolType.HTTP;
    private IdentifierConfig identifier;
    private ProtocolConfig protocolConfig;
    private String password;
    private List<String> requestTransformations;
    private List<String> responseTransformations;
}
