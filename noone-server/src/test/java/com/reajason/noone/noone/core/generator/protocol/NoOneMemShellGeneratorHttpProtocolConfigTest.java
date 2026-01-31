package com.reajason.noone.noone.core.generator.protocol;

import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.noone.core.client.HttpBodyTemplateEngine;
import com.reajason.noone.core.client.HttpRequestBodyType;
import com.reajason.noone.core.generator.NoOneConfig;
import com.reajason.noone.core.generator.NoOneMemShellGenerator;
import com.reajason.noone.core.shelltool.NoOneServlet;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.config.HttpProtocolConfig;
import com.reajason.noone.server.profile.config.HttpResponseBodyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.objectweb.asm.ClassReader;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NoOneMemShellGeneratorHttpProtocolConfigTest {

    private static final String TEST_PAYLOAD = "PAYLOAD";
    private static final byte[] TEST_PAYLOAD_BYTES = TEST_PAYLOAD.getBytes(UTF_8);

    @Nested
    @DisplayName("getArgFromRequest Tests")
    class GetArgFromRequestTests {

        @Nested
        @DisplayName("FORM_URLENCODED")
        class FormUrlencodedTests {

            @Test
            @DisplayName("should extract payload from simple parameter")
            void shouldExtractPayloadFromSimpleParameter() throws Exception {
                String template = "q={{payload}}";
                HttpProtocolConfig config = new HttpProtocolConfig();
                config.setRequestBodyType(com.reajason.noone.server.profile.config.HttpRequestBodyType.FORM_URLENCODED);
                config.setRequestTemplate(template);

                byte[] bytes = generateBytes(config);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                HttpServletRequest request = mock(HttpServletRequest.class);
                when(request.getParameter("q")).thenReturn(TEST_PAYLOAD);

                byte[] extracted = invokeGetArgFromRequest(generated, instance, request);
                assertEquals(TEST_PAYLOAD, new String(extracted, UTF_8));
            }

            @Test
            @DisplayName("should extract payload with prefix and suffix")
            void shouldExtractPayloadWithPrefixSuffix() throws Exception {
                String template = "q=prefix{{payload}}suffix";
                HttpProtocolConfig config = new HttpProtocolConfig();
                config.setRequestBodyType(com.reajason.noone.server.profile.config.HttpRequestBodyType.FORM_URLENCODED);
                config.setRequestTemplate(template);

                byte[] bytes = generateBytes(config);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                HttpServletRequest request = mock(HttpServletRequest.class);
                when(request.getParameter("q")).thenReturn("prefix" + TEST_PAYLOAD + "suffix");

                byte[] extracted = invokeGetArgFromRequest(generated, instance, request);
                assertEquals(TEST_PAYLOAD, new String(extracted, UTF_8));
            }
        }

        @Nested
        @DisplayName("MULTIPART_FORM_DATA")
        class MultipartFormDataTests {

            @Test
            @DisplayName("should extract payload from multipart body")
            void shouldExtractPayloadFromMultipartBody() throws Exception {
                String template = HttpBodyTemplateEngine.defaultRequestTemplate(HttpRequestBodyType.MULTIPART_FORM_DATA);
                HttpBodyTemplateEngine.EncodedBody encoded = HttpBodyTemplateEngine.encodeRequestBody(
                        HttpRequestBodyType.MULTIPART_FORM_DATA,
                        template,
                        TEST_PAYLOAD
                );

                HttpProtocolConfig config = new HttpProtocolConfig();
                config.setRequestBodyType(com.reajason.noone.server.profile.config.HttpRequestBodyType.MULTIPART_FORM_DATA);
                config.setRequestTemplate(template);

                byte[] bytes = generateBytes(config);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                HttpServletRequest request = mock(HttpServletRequest.class);
                when(request.getContentType()).thenReturn(encoded.contentType());
                when(request.getInputStream()).thenReturn(newServletInputStream(encoded.bytes()));

                byte[] extracted = invokeGetArgFromRequest(generated, instance, request);
                assertEquals(TEST_PAYLOAD, new String(extracted, UTF_8));
            }
        }

        @Nested
        @DisplayName("JSON")
        class JsonTests {

            @Test
            @DisplayName("should extract payload from JSON body")
            void shouldExtractPayloadFromJsonBody() throws Exception {
                String template = "{\"data\":\"{{payload}}\"}";
                HttpBodyTemplateEngine.EncodedBody encoded = HttpBodyTemplateEngine.encodeRequestBody(
                        HttpRequestBodyType.JSON,
                        template,
                        TEST_PAYLOAD
                );

                HttpProtocolConfig config = new HttpProtocolConfig();
                config.setRequestBodyType(com.reajason.noone.server.profile.config.HttpRequestBodyType.JSON);
                config.setRequestTemplate(template);

                byte[] bytes = generateBytes(config);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                HttpServletRequest request = mock(HttpServletRequest.class);
                when(request.getInputStream()).thenReturn(newServletInputStream(encoded.bytes()));

                byte[] extracted = invokeGetArgFromRequest(generated, instance, request);
                assertEquals(TEST_PAYLOAD, new String(extracted, UTF_8));
            }
        }

        @Nested
        @DisplayName("XML")
        class XmlTests {

            @Test
            @DisplayName("should extract payload from XML body")
            void shouldExtractPayloadFromXmlBody() throws Exception {
                String template = "<data>{{payload}}</data>";
                HttpBodyTemplateEngine.EncodedBody encoded = HttpBodyTemplateEngine.encodeRequestBody(
                        HttpRequestBodyType.XML,
                        template,
                        TEST_PAYLOAD
                );

                HttpProtocolConfig config = new HttpProtocolConfig();
                config.setRequestBodyType(com.reajason.noone.server.profile.config.HttpRequestBodyType.XML);
                config.setRequestTemplate(template);

                byte[] bytes = generateBytes(config);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                HttpServletRequest request = mock(HttpServletRequest.class);
                when(request.getInputStream()).thenReturn(newServletInputStream(encoded.bytes()));

                byte[] extracted = invokeGetArgFromRequest(generated, instance, request);
                assertEquals(TEST_PAYLOAD, new String(extracted, UTF_8));
            }
        }

        @Nested
        @DisplayName("TEXT")
        class TextTests {

            @Test
            @DisplayName("should extract payload from text body")
            void shouldExtractPayloadFromTextBody() throws Exception {
                String template = "hello{{payload}}world";
                HttpBodyTemplateEngine.EncodedBody encoded = HttpBodyTemplateEngine.encodeRequestBody(
                        HttpRequestBodyType.TEXT,
                        template,
                        TEST_PAYLOAD
                );

                HttpProtocolConfig config = new HttpProtocolConfig();
                config.setRequestBodyType(com.reajason.noone.server.profile.config.HttpRequestBodyType.TEXT);
                config.setRequestTemplate(template);

                byte[] bytes = generateBytes(config);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                HttpServletRequest request = mock(HttpServletRequest.class);
                when(request.getInputStream()).thenReturn(newServletInputStream(encoded.bytes()));

                byte[] extracted = invokeGetArgFromRequest(generated, instance, request);
                assertEquals(TEST_PAYLOAD, new String(extracted, UTF_8));
            }
        }

        @Nested
        @DisplayName("BINARY")
        class BinaryTests {

            @Test
            @DisplayName("should extract payload from binary body")
            void shouldExtractPayloadFromBinaryBody() throws Exception {
                String template = "<base64>aGVsbG8=</base64>{{payload}}";
                HttpBodyTemplateEngine.EncodedBody encoded = HttpBodyTemplateEngine.encodeRequestBody(
                        HttpRequestBodyType.BINARY,
                        template,
                        TEST_PAYLOAD
                );

                HttpProtocolConfig config = new HttpProtocolConfig();
                config.setRequestBodyType(com.reajason.noone.server.profile.config.HttpRequestBodyType.BINARY);
                config.setRequestTemplate(template);

                byte[] bytes = generateBytes(config);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                HttpServletRequest request = mock(HttpServletRequest.class);
                when(request.getInputStream()).thenReturn(newServletInputStream(encoded.bytes()));

                byte[] extracted = invokeGetArgFromRequest(generated, instance, request);
                assertEquals(TEST_PAYLOAD, new String(extracted, UTF_8));
            }
        }
    }

    @Nested
    @DisplayName("wrapResData Tests")
    class WrapResDataTests {

        @Test
        @DisplayName("should wrap payload with TEXT template")
        void shouldWrapPayloadWithTextTemplate() throws Exception {
            String template = "hello{{payload}}world";
            HttpProtocolConfig config = new HttpProtocolConfig();
            config.setResponseBodyType(HttpResponseBodyType.TEXT);
            config.setResponseTemplate(template);

            byte[] bytes = generateBytes(config);
            Class<?> generated = loadGeneratedClass(bytes);
            Object instance = generated.getDeclaredConstructor().newInstance();

            byte[] wrapped = invokeWrapResData(generated, instance, TEST_PAYLOAD_BYTES);
            assertEquals("hello" + TEST_PAYLOAD + "world", new String(wrapped, UTF_8));
        }

        @Test
        @DisplayName("should wrap payload with JSON template")
        void shouldWrapPayloadWithJsonTemplate() throws Exception {
            String template = "{\"result\":\"{{payload}}\"}";
            HttpProtocolConfig config = new HttpProtocolConfig();
            config.setResponseBodyType(HttpResponseBodyType.JSON);
            config.setResponseTemplate(template);

            byte[] bytes = generateBytes(config);
            Class<?> generated = loadGeneratedClass(bytes);
            Object instance = generated.getDeclaredConstructor().newInstance();

            byte[] wrapped = invokeWrapResData(generated, instance, TEST_PAYLOAD_BYTES);
            assertEquals("{\"result\":\"" + TEST_PAYLOAD + "\"}", new String(wrapped, UTF_8));
        }

        @Test
        @DisplayName("should wrap payload with XML template")
        void shouldWrapPayloadWithXmlTemplate() throws Exception {
            String template = "<result>{{payload}}</result>";
            HttpProtocolConfig config = new HttpProtocolConfig();
            config.setResponseBodyType(HttpResponseBodyType.XML);
            config.setResponseTemplate(template);

            byte[] bytes = generateBytes(config);
            Class<?> generated = loadGeneratedClass(bytes);
            Object instance = generated.getDeclaredConstructor().newInstance();

            byte[] wrapped = invokeWrapResData(generated, instance, TEST_PAYLOAD_BYTES);
            assertEquals("<result>" + TEST_PAYLOAD + "</result>", new String(wrapped, UTF_8));
        }

        @Test
        @DisplayName("should wrap payload with BINARY template")
        void shouldWrapPayloadWithBinaryTemplate() throws Exception {
            String template = "<base64>aGVsbG8=</base64>{{payload}}";
            HttpProtocolConfig config = new HttpProtocolConfig();
            config.setResponseBodyType(HttpResponseBodyType.BINARY);
            config.setResponseTemplate(template);

            byte[] bytes = generateBytes(config);
            Class<?> generated = loadGeneratedClass(bytes);
            Object instance = generated.getDeclaredConstructor().newInstance();

            byte[] wrapped = invokeWrapResData(generated, instance, TEST_PAYLOAD_BYTES);
            // "hello" (decoded from base64) + PAYLOAD
            byte[] expected = new byte[5 + TEST_PAYLOAD_BYTES.length];
            System.arraycopy("hello".getBytes(UTF_8), 0, expected, 0, 5);
            System.arraycopy(TEST_PAYLOAD_BYTES, 0, expected, 5, TEST_PAYLOAD_BYTES.length);
            assertArrayEquals(expected, wrapped);
        }
    }

    @Nested
    @DisplayName("wrapResponse Tests")
    class WrapResponseTests {

        @Nested
        @DisplayName("Status Code")
        class StatusCodeTests {

            @Test
            @DisplayName("should set custom status code 418")
            void shouldSetCustomStatusCode() throws Exception {
                HttpProtocolConfig config = new HttpProtocolConfig();
                config.setResponseStatusCode(418);

                byte[] bytes = generateBytes(config);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                HttpServletResponse response = mock(HttpServletResponse.class);
                invokeWrapResponse(generated, instance, response);

                verify(response).setStatus(418);
            }

            @Test
            @DisplayName("should set status code 201 for created resource")
            void shouldSetDifferentStatusCode() throws Exception {
                HttpProtocolConfig config = new HttpProtocolConfig();
                config.setResponseStatusCode(201);

                byte[] bytes = generateBytes(config);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                HttpServletResponse response = mock(HttpServletResponse.class);
                invokeWrapResponse(generated, instance, response);

                verify(response).setStatus(201);
            }
        }

        @Nested
        @DisplayName("Headers")
        class HeadersTests {

            @Test
            @DisplayName("should set single header")
            void shouldSetSingleHeader() throws Exception {
                HttpProtocolConfig config = new HttpProtocolConfig();
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("X-Custom", "value");
                config.setResponseHeaders(headers);

                byte[] bytes = generateBytes(config);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                HttpServletResponse response = mock(HttpServletResponse.class);
                invokeWrapResponse(generated, instance, response);

                verify(response).setHeader("X-Custom", "value");
            }

            @Test
            @DisplayName("should set multiple headers in order")
            void shouldSetMultipleHeaders() throws Exception {
                HttpProtocolConfig config = new HttpProtocolConfig();
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("X-First", "first-value");
                headers.put("X-Second", "second-value");
                headers.put("Content-Type", "application/json");
                config.setResponseHeaders(headers);

                byte[] bytes = generateBytes(config);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                HttpServletResponse response = mock(HttpServletResponse.class);
                invokeWrapResponse(generated, instance, response);

                InOrder inOrder = inOrder(response);
                inOrder.verify(response).setHeader("X-First", "first-value");
                inOrder.verify(response).setHeader("X-Second", "second-value");
                inOrder.verify(response).setHeader("Content-Type", "application/json");
            }
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("should apply complete HTTP protocol config via advice mapping")
        void httpProtocolConfig_shouldBeAppliedViaAdviceMapping() throws Exception {
            HttpProtocolConfig http = new HttpProtocolConfig();
            http.setRequestBodyType(com.reajason.noone.server.profile.config.HttpRequestBodyType.FORM_URLENCODED);
            http.setRequestTemplate("username=admin&action=login&q={{payload}}&token=123456");
            http.setResponseBodyType(HttpResponseBodyType.TEXT);
            http.setResponseTemplate("hello{{payload}}world");
            http.setResponseStatusCode(418);
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            responseHeaders.put("X-Test", "1");
            responseHeaders.put("Content-Type", "text/plain; charset=utf-8");
            http.setResponseHeaders(responseHeaders);

            byte[] bytes = generateBytes(http);
            Class<?> generated = loadGeneratedClass(bytes);

            Object instance = generated.getDeclaredConstructor().newInstance();

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getParameter("q")).thenReturn("PAYLOAD");

            byte[] extracted = invokeGetArgFromRequest(generated, instance, request);
            assertEquals("PAYLOAD", new String(extracted, StandardCharsets.UTF_8));

            byte[] wrappedData = invokeWrapResData(generated, instance, "PAYLOAD".getBytes(StandardCharsets.UTF_8));
            assertEquals("helloPAYLOADworld", new String(wrappedData, StandardCharsets.UTF_8));

            HttpServletResponse response = mock(HttpServletResponse.class);
            invokeWrapResponse(generated, instance, response);

            verify(response).setStatus(418);
            verify(response).setHeader("X-Test", "1");
            verify(response).setHeader("Content-Type", "text/plain; charset=utf-8");
            verifyNoMoreInteractions(response);
        }
    }

    // Helper methods for reflection-based invocation

    private static byte[] invokeGetArgFromRequest(Class<?> clazz, Object instance, HttpServletRequest request) throws Exception {
        Method method = clazz.getDeclaredMethod("getArgFromRequest", Object.class);
        method.setAccessible(true);
        return (byte[]) method.invoke(instance, request);
    }

    private static byte[] invokeWrapResData(Class<?> clazz, Object instance, byte[] payload) throws Exception {
        Method method = clazz.getDeclaredMethod("wrapResData", byte[].class);
        method.setAccessible(true);
        return (byte[]) method.invoke(instance, payload);
    }

    private static void invokeWrapResponse(Class<?> clazz, Object instance, HttpServletResponse response) throws Exception {
        Method method = clazz.getDeclaredMethod("wrapResponse", Object.class);
        method.setAccessible(true);
        method.invoke(instance, response);
    }

    // Helper methods for test setup

    private static byte[] generateBytes(HttpProtocolConfig http) {
        ShellConfig shellConfig = ShellConfig.builder()
                .server("Tomcat")
                .shellTool("Custom")
                .shellType("Servlet")
                .targetJreVersion(52)
                .build();

        Profile profile = new Profile();
        profile.setProtocolConfig(http);

        NoOneConfig noOneConfig = new NoOneConfig();
        noOneConfig.setShellClass(NoOneServlet.class);
        noOneConfig.setShellClassName("com.reajason.noone.test.GeneratedNoOneServlet");
        noOneConfig.setProfile(profile);

        NoOneMemShellGenerator generator = new NoOneMemShellGenerator(shellConfig, noOneConfig);
        return generator.getBytes();
    }

    private static Class<?> loadGeneratedClass(byte[] bytes) {
        String internalName = new ClassReader(bytes).getClassName();
        String className = internalName.replace('/', '.');

        class DefiningClassLoader extends ClassLoader {
            DefiningClassLoader(ClassLoader parent) {
                super(parent);
            }

            Class<?> define() {
                return defineClass(className, bytes, 0, bytes.length);
            }
        }

        return new DefiningClassLoader(NoOneServlet.class.getClassLoader()).define();
    }

    private static ServletInputStream newServletInputStream(byte[] bytes) {
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        return new ServletInputStream() {
            @Override
            public int read() {
                return in.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return in.read(b, off, len);
            }
        };
    }
}
