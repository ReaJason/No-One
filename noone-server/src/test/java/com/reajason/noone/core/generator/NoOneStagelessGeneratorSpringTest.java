package com.reajason.noone.core.generator;

import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.utils.CommonUtil;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.core.generator.memshell.NoOneStagelessGenerator;
import com.reajason.noone.core.shelltool.NoOneStagelessServlet;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.ProfileRepository;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NoOneStagelessGeneratorSpringTest {

    @Autowired
    private ProfileRepository profileRepository;

    @Test
    @SneakyThrows
    void testAuthed() {
        NoOneConfig noOneConfig = new NoOneConfig();
        noOneConfig.setShellClass(NoOneStagelessServlet.class);
        noOneConfig.setShellClassName(CommonUtil.generateClassName());
        Profile profile = profileRepository.getByNameEquals("JSON Example");
        noOneConfig.setCoreProfile(profile);
        ShellConfig shellConfig = ShellConfig.builder()
                .shellType(ShellType.SERVLET)
                .build();
        NoOneStagelessGenerator noOneStagelessGenerator = new NoOneStagelessGenerator(shellConfig, noOneConfig);
        byte[] bytes = noOneStagelessGenerator.getBytes();
//        Files.write(Paths.get("hello.class"), bytes);
    }

}