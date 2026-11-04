package com.reajason.noone.core.generator.memshell;

import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.generator.ByteBuddyShellGenerator;
import com.reajason.noone.core.ProfileVisitorWrapper;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.core.profile.Profile;
import net.bytebuddy.dynamic.DynamicType;

public class NoOneStagingGenerator extends ByteBuddyShellGenerator<NoOneConfig> {

    public NoOneStagingGenerator(ShellConfig shellConfig, NoOneConfig shellToolConfig) {
        super(shellConfig, shellToolConfig);
    }

    @Override
    protected DynamicType.Builder<?> getBuilder() {
        DynamicType.Builder<?> builder = redefineTemplate();
        Profile profile = shellToolConfig.getLoaderProfile();
        return ProfileVisitorWrapper.extend(builder, profile,
                shellConfig.getShellType(), shellToolConfig.getShellClassName());
    }
}
