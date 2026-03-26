package com.reajason.noone.core.transform;

import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.core.profile.config.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransformConfigTest {

    @Test
    void fromProfile_httpJson_shouldResolveJsonContentType() {
        Profile profile = new Profile();
        profile.setPassword("secret");
        profile.setRequestTransformations(List.of("Gzip", "XOR", "Base64"));
        profile.setResponseTransformations(List.of("Gzip", "TripleDES", "Hex"));

        HttpProtocolConfig httpConfig = new HttpProtocolConfig();
        httpConfig.setRequestBodyType(HttpRequestBodyType.JSON);
        httpConfig.setResponseBodyType(HttpResponseBodyType.JSON);
        httpConfig.setRequestTemplate("{\"data\": \"{{payload}}\"}");
        httpConfig.setResponseTemplate("{\"res\": \"{{payload}}\"}");
        profile.setProtocolConfig(httpConfig);

        TransformConfig tc = TransformConfig.fromProfile(profile);

        assertEquals("secret", tc.password());
        assertEquals(HttpRequestBodyType.JSON, tc.requestBodyType());
        assertEquals(HttpResponseBodyType.JSON, tc.responseBodyType());
        assertEquals("{\"data\": \"{{payload}}\"}", tc.requestTemplate());
        assertEquals("{\"res\": \"{{payload}}\"}", tc.responseTemplate());
        assertEquals("application/json; charset=utf-8", tc.contentType());
        assertNotNull(tc.requestSpec());
        assertNotNull(tc.responseSpec());
    }

    @Test
    void fromProfile_httpMultipartFormData_shouldGenerateBoundaryAndContentType() {
        Profile profile = new Profile();
        profile.setPassword("pass");
        profile.setRequestTransformations(List.of("Gzip", "AES", "Base64"));
        profile.setResponseTransformations(List.of("Gzip", "AES", "Base64"));

        HttpProtocolConfig httpConfig = new HttpProtocolConfig();
        httpConfig.setRequestBodyType(HttpRequestBodyType.MULTIPART_FORM_DATA);
        httpConfig.setRequestTemplate("--{{boundary}}\r\nContent-Disposition: form-data; name=\"file\"\r\n\r\n{{payload}}\r\n--{{boundary}}--");
        profile.setProtocolConfig(httpConfig);

        TransformConfig tc = TransformConfig.fromProfile(profile);

        assertEquals(HttpRequestBodyType.MULTIPART_FORM_DATA, tc.requestBodyType());
        assertTrue(tc.contentType().startsWith("multipart/form-data; boundary=NoOneBoundary"));
        assertFalse(tc.requestTemplate().contains("{{boundary}}"));
        assertTrue(tc.requestTemplate().contains("NoOneBoundary"));
    }

    @Test
    void fromProfile_httpNullBodyType_shouldDefaultContentType() {
        Profile profile = new Profile();
        profile.setPassword("pwd");
        profile.setRequestTransformations(null);
        profile.setResponseTransformations(null);

        HttpProtocolConfig httpConfig = new HttpProtocolConfig();
        profile.setProtocolConfig(httpConfig);

        TransformConfig tc = TransformConfig.fromProfile(profile);

        assertEquals("application/x-www-form-urlencoded; charset=utf-8", tc.contentType());
        assertNull(tc.requestBodyType());
    }

    @Test
    void fromProfile_webSocket_shouldUseBinaryBodyTypes() {
        Profile profile = new Profile();
        profile.setPassword("ws-pass");
        profile.setRequestTransformations(List.of("Gzip", "XOR", "Base64"));
        profile.setResponseTransformations(List.of("Gzip", "AES", "Base64"));

        WebSocketProtocolConfig wsConfig = new WebSocketProtocolConfig();
        wsConfig.setMessageTemplate("ws-req-template");
        wsConfig.setResponseTemplate("ws-res-template");
        profile.setProtocolConfig(wsConfig);

        TransformConfig tc = TransformConfig.fromProfile(profile);

        assertEquals("ws-pass", tc.password());
        assertEquals(HttpRequestBodyType.BINARY, tc.requestBodyType());
        assertEquals("ws-req-template", tc.requestTemplate());
        assertEquals(HttpResponseBodyType.BINARY, tc.responseBodyType());
        assertEquals("ws-res-template", tc.responseTemplate());
        assertNull(tc.contentType());
    }

    @Test
    void fromProfile_dubbo_shouldUseBinaryWhenTemplatesPresent() {
        Profile profile = new Profile();
        profile.setPassword("dubbo-pass");
        profile.setRequestTransformations(List.of("Gzip", "AES", "Base64"));
        profile.setResponseTransformations(List.of("Gzip", "AES", "Base64"));

        DubboProtocolConfig dubboConfig = new DubboProtocolConfig();
        dubboConfig.setRequestTemplate("dubbo-req");
        dubboConfig.setResponseTemplate("dubbo-res");
        profile.setProtocolConfig(dubboConfig);

        TransformConfig tc = TransformConfig.fromProfile(profile);

        assertEquals(HttpRequestBodyType.BINARY, tc.requestBodyType());
        assertEquals("dubbo-req", tc.requestTemplate());
        assertEquals(HttpResponseBodyType.BINARY, tc.responseBodyType());
        assertEquals("dubbo-res", tc.responseTemplate());
        assertNull(tc.contentType());
    }

    @Test
    void fromProfile_dubbo_shouldBeNullWhenTemplatesEmpty() {
        Profile profile = new Profile();
        profile.setPassword("dubbo-pass");
        profile.setRequestTransformations(List.of("Gzip", "XOR", "Hex"));
        profile.setResponseTransformations(List.of("Gzip", "XOR", "Hex"));

        DubboProtocolConfig dubboConfig = new DubboProtocolConfig();
        dubboConfig.setRequestTemplate("");
        dubboConfig.setResponseTemplate(null);
        profile.setProtocolConfig(dubboConfig);

        TransformConfig tc = TransformConfig.fromProfile(profile);

        assertNull(tc.requestBodyType());
        assertNull(tc.requestTemplate());
        assertNull(tc.responseBodyType());
        assertNull(tc.responseTemplate());
    }

    @Test
    void fromProfile_nullProtocolConfig_shouldReturnAllNullCodecFields() {
        Profile profile = new Profile();
        profile.setPassword("test");
        profile.setProtocolConfig(null);
        profile.setRequestTransformations(List.of("Gzip", "AES", "Base64"));
        profile.setResponseTransformations(List.of("Gzip", "AES", "Base64"));

        TransformConfig tc = TransformConfig.fromProfile(profile);

        assertEquals("test", tc.password());
        assertNull(tc.requestBodyType());
        assertNull(tc.requestTemplate());
        assertNull(tc.responseBodyType());
        assertNull(tc.responseTemplate());
        assertNull(tc.contentType());
        assertNotNull(tc.requestSpec());
        assertNotNull(tc.responseSpec());
    }
}
