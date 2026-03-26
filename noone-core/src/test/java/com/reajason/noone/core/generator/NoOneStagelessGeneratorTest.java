package com.reajason.noone.core.generator;

import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.utils.CommonUtil;
import com.reajason.noone.core.Constants;
import com.reajason.noone.core.profile.config.HttpRequestBodyType;
import com.reajason.noone.core.profile.config.HttpResponseBodyType;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.core.generator.memshell.NoOneStagelessGenerator;
import com.reajason.noone.core.shelltool.NoOneStagelessNettyHandler;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.core.profile.config.*;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.List;

class NoOneStagelessGeneratorTest {

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

        noOneConfig.setShellClass(NoOneStagelessNettyHandler.class);
        noOneConfig.setCoreProfile(profile);
        ShellConfig shellConfig = ShellConfig.builder()
                .shellType(ShellType.NETTY_HANDLER)
                .shellTool(Constants.NO_ONE)
                .build();
        NoOneStagelessGenerator noOneStagelessGenerator = new NoOneStagelessGenerator(shellConfig, noOneConfig);
        byte[] bytes = noOneStagelessGenerator.getBytes();
//        Files.write(Paths.get("hello.class"), bytes);
    }

}