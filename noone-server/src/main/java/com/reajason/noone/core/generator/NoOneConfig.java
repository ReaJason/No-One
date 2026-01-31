package com.reajason.noone.core.generator;

import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.noone.server.profile.Profile;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoOneConfig extends ShellToolConfig {
    private Profile profile;
}
