package com.reajason.noone.noone.core.client;

import com.reajason.noone.core.client.HttpBodyTemplateEngine;
import com.reajason.noone.core.client.HttpRequestBodyType;
import com.reajason.noone.core.client.HttpResponseBodyType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HttpBodyTemplateEngineTest {

    @Test
    void encodeRequestBody_formUrlencoded_defaultTemplate_urlEncodesPayload() {
        HttpBodyTemplateEngine.EncodedBody encoded = HttpBodyTemplateEngine.encodeRequestBody(
                HttpRequestBodyType.FORM_URLENCODED,
                null,
                "a b"
        );

        String body = new String(encoded.bytes(), StandardCharsets.UTF_8);
        assertEquals("application/x-www-form-urlencoded; charset=utf-8", encoded.contentType());
        assertTrue(body.contains("q=a+b"));
    }

    @Test
    void encodeRequestBody_text_defaultTemplate() {
        HttpBodyTemplateEngine.EncodedBody encoded = HttpBodyTemplateEngine.encodeRequestBody(
                HttpRequestBodyType.TEXT,
                null,
                "PAY"
        );

        assertEquals("text/plain; charset=utf-8", encoded.contentType());
        assertEquals("helloPAYworld", new String(encoded.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void encodeRequestBody_json_defaultTemplate() {
        HttpBodyTemplateEngine.EncodedBody encoded = HttpBodyTemplateEngine.encodeRequestBody(
                HttpRequestBodyType.JSON,
                null,
                "PAY"
        );

        assertEquals("application/json; charset=utf-8", encoded.contentType());
        assertEquals("{\"hello\": \"PAY\"}", new String(encoded.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void encodeRequestBody_binary_defaultTemplate_supportsBase64Tag() {
        HttpBodyTemplateEngine.EncodedBody encoded = HttpBodyTemplateEngine.encodeRequestBody(
                HttpRequestBodyType.BINARY,
                null,
                "XYZ"
        );

        assertEquals("application/octet-stream", encoded.contentType());
        assertEquals("helloXYZ", new String(encoded.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void encodeRequestBody_multipart_defaultTemplate_supportsHexTagAndBoundary() {
        HttpBodyTemplateEngine.EncodedBody encoded = HttpBodyTemplateEngine.encodeRequestBody(
                HttpRequestBodyType.MULTIPART_FORM_DATA,
                null,
                "XYZ"
        );

        assertNotNull(encoded.contentType());
        assertTrue(encoded.contentType().startsWith("multipart/form-data; boundary="));

        String boundary = encoded.contentType().substring("multipart/form-data; boundary=".length());
        byte[] bytes = encoded.bytes();

        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains(boundary));

        byte[] pngMagic = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        assertTrue(indexOf(bytes, pngMagic) >= 0);
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("XYZ"));
    }

    @Test
    void extractResponsePayload_textTemplate() {
        String extracted = HttpBodyTemplateEngine.extractResponsePayload(
                HttpResponseBodyType.TEXT,
                "hello{{payload}}world",
                "helloABCworld".getBytes(StandardCharsets.UTF_8)
        );
        assertEquals("ABC", extracted);
    }

    @Test
    void extractResponsePayload_jsonTemplate() {
        String extracted = HttpBodyTemplateEngine.extractResponsePayload(
                HttpResponseBodyType.JSON,
                "{\"hello\": \"{{payload}}\"}",
                "{\"hello\": \"ABC\"}".getBytes(StandardCharsets.UTF_8)
        );
        assertEquals("ABC", extracted);
    }

    @Test
    void extractResponsePayload_binaryTemplate_supportsBase64Tag() {
        byte[] response = "helloABC".getBytes(StandardCharsets.UTF_8);
        String extracted = HttpBodyTemplateEngine.extractResponsePayload(
                HttpResponseBodyType.BINARY,
                "<base64>aGVsbG8=</base64>{{payload}}",
                response
        );
        assertEquals("ABC", extracted);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        if (needle.length == 0) {
            return 0;
        }
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
