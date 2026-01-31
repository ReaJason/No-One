package com.reajason.noone.noone.core.generator;

import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.utils.CommonUtil;
import com.reajason.noone.core.generator.NoOneConfig;
import com.reajason.noone.core.generator.NoOneMemShellGenerator;
import com.reajason.noone.core.shelltool.NoOneServlet;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.ProfileRepository;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootTest
class NoOneMemShellGeneratorSpringTest {

    @Autowired
    private ProfileRepository profileRepository;

    @Test
    @SneakyThrows
    void testAuthed() {
        NoOneConfig noOneConfig = new NoOneConfig();
        noOneConfig.setShellClass(NoOneServlet.class);
        noOneConfig.setShellClassName(CommonUtil.generateClassName());
        Profile profile = profileRepository.getByNameEquals("JSON Example");
        noOneConfig.setProfile(profile);
        ShellConfig shellConfig = ShellConfig.builder()
                .shellType(ShellType.SERVLET)
                .build();
        NoOneMemShellGenerator noOneMemShellGenerator = new NoOneMemShellGenerator(shellConfig, noOneConfig);
        byte[] bytes = noOneMemShellGenerator.getBytes();
        Files.write(Paths.get("hello.class"), bytes);
    }

}