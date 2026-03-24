package com.reajason.noone.core.generator.config;

import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.noone.core.profile.Profile;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@SuperBuilder
public class NoOneConfig extends ShellToolConfig {

    public NoOneConfig(Profile coreProfile) {
        this.coreProfile = coreProfile;
    }

    private Profile coreProfile;
    private Profile loaderProfile;
}
