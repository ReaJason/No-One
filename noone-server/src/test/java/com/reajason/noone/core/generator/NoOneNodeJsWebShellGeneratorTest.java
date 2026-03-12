package com.reajason.noone.core.generator;

import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.config.*;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NoOneNodeJsWebShellGeneratorTest {

    @Test
    void shouldGenerateMjsWebShell() {
        NoOneNodeJsWebShellGenerator generator = new NoOneNodeJsWebShellGenerator(new NoOneConfig(createProfile()));

        String mjs = generator.generateMjs();

        assertAll(
                () -> assertTrue(mjs.contains("http.Server.prototype.emit")),
                () -> assertTrue(mjs.contains("initNoOneCore")),
                () -> assertTrue(mjs.contains("NOONE_CORE_CODE")),
                () -> assertTrue(mjs.contains("global.NoOneCore"))
        );
    }

    @Test
    void shouldFillAllPlaceholders() {
        NoOneNodeJsWebShellGenerator generator = new NoOneNodeJsWebShellGenerator(new NoOneConfig(createProfile()));

        String content = generator.generateMjs();

        assertAll(
                () -> assertFalse(content.contains("__IS_AUTHED__")),
                () -> assertFalse(content.contains("__GET_ARG_FROM_CONTENT__")),
                () -> assertFalse(content.contains("__TRANSFORM_REQ_PAYLOAD__")),
                () -> assertFalse(content.contains("__TRANSFORM_RES_DATA__")),
                () -> assertFalse(content.contains("__WRAP_RES_DATA__")),
                () -> assertFalse(content.contains("__WRAP_RESPONSE__")),
                () -> assertFalse(content.contains("__EXTRA_HELPERS__")),
                () -> assertFalse(content.contains("__CORE_CODE_BASE64__"))
        );
    }

    @Test
    void shouldFillProtocolAndTransformationPlaceholders() {
        NoOneNodeJsWebShellGenerator generator = new NoOneNodeJsWebShellGenerator(new NoOneConfig(createProfile()));

        String content = generator.generateMjs();

        assertAll(
                () -> assertTrue(content.contains("req.headers['x-test']")),
                () -> assertTrue(content.contains("content.slice(")),
                () -> assertTrue(content.contains("res.writeHead(")),
                () -> assertTrue(content.contains("'X-Trace': 'enabled'")),
                () -> assertTrue(content.contains("function aesDecrypt")),
                () -> assertTrue(content.contains("function gzipCompress")),
                () -> assertTrue(content.contains("function decodeBase64")),
                () -> assertTrue(content.contains("function encodeHex"))
        );
    }

    private static Profile createProfile() {
        Profile profile = new Profile();
        profile.setName("demo");
        profile.setPassword("secret");

        IdentifierConfig identifier = new IdentifierConfig();
        identifier.setLocation(IdentifierLocation.HEADER);
        identifier.setOperator(IdentifierOperator.EQUALS);
        identifier.setName("X-Test");
        identifier.setValue("demo");
        profile.setIdentifier(identifier);

        HttpProtocolConfig protocol = new HttpProtocolConfig();
        protocol.setRequestMethod("POST");
        protocol.setRequestBodyType(HttpRequestBodyType.TEXT);
        protocol.setRequestTemplate("prefix{{payload}}suffix");
        protocol.setResponseBodyType(HttpResponseBodyType.TEXT);
        protocol.setResponseTemplate("before{{payload}}after");
        protocol.setResponseStatusCode(202);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Trace", "enabled");
        protocol.setResponseHeaders(headers);
        profile.setProtocolConfig(protocol);

        profile.setRequestTransformations(List.of("Gzip", "AES", "Base64"));
        profile.setResponseTransformations(List.of("Gzip", "AES", "Hex"));
        return profile;
    }
}
