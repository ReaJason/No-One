package com.reajason.noone.core.generator;

import com.reajason.javaweb.Server;
import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.noone.core.Constants;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.core.shelltool.NoOneStagelessFilter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JavaMemShellGeneratorTest {

    @Test
    void shouldGenerateStagelessShellThroughSpiProvider() {
        Profile profile = new Profile();
        profile.setPassword("noone");
        profile.setRequestTransformations(List.of());
        profile.setResponseTransformations(List.of());

        ShellConfig shellConfig = ShellConfig.builder()
                .server(Server.Tomcat)
                .shellTool(Constants.NO_ONE)
                .shellType(ShellType.FILTER)
                .targetJreVersion(52)
                .build();
        NoOneConfig noOneConfig = NoOneConfig.builder()
                .shellClassName("com.reajason.noone.test.GeneratedNoOneFilter")
                .coreProfile(profile)
                .build();

        JavaMemShellGenerator generator = new JavaMemShellGenerator();
        MemShellResult result = generator.generate(shellConfig,
                InjectorConfig.builder().urlPattern("/*").build(), noOneConfig);

        assertNotNull(result);
        assertEquals("com.reajason.noone.test.GeneratedNoOneFilter", result.getShellClassName());
        assertEquals(NoOneStagelessFilter.class.getName(),
                result.getShellToolConfig().getShellTemplateClassName());
        assertTrue(result.getShellBytes().length > 0);
        assertTrue(result.getInjectorBytes().length > 0);
        assertNotNull(result.getShellBytesBase64Str());
        assertNotNull(result.getInjectorBytesBase64Str());

        Map<?, ?> tomcatTools = generator.getConfigMap().get(Server.Tomcat);
        assertNotNull(tomcatTools);
        assertTrue(tomcatTools.containsKey(Constants.NO_ONE));
    }
}
