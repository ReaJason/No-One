package com.reajason.noone.noone.core.generator.protocol;

import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.noone.core.client.HttpBodyTemplateEngine;
import com.reajason.noone.core.client.HttpRequestBodyType;
import com.reajason.noone.core.generator.NoOneConfig;
import com.reajason.noone.core.generator.NoOneMemShellGenerator;
import com.reajason.noone.core.shelltool.NoOneWebFilter;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.config.HttpProtocolConfig;
import com.reajason.noone.server.profile.config.HttpResponseBodyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class NoOneMemShellGeneratorReactorHttpProtocolConfigTest {

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
                assertNoAdviceReferences(bytes);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                MockServerHttpRequest request = MockServerHttpRequest.post("/")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body("q=" + TEST_PAYLOAD);
                MockServerWebExchange exchange = MockServerWebExchange.from(request);

                byte[] extracted = invokeGetArgFromRequest(generated, instance, exchange);
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
                assertNoAdviceReferences(bytes);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                MockServerHttpRequest request = MockServerHttpRequest.post("/")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body("q=prefix" + TEST_PAYLOAD + "suffix");
                MockServerWebExchange exchange = MockServerWebExchange.from(request);

                byte[] extracted = invokeGetArgFromRequest(generated, instance, exchange);
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
                assertNoAdviceReferences(bytes);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                MockServerHttpRequest request = MockServerHttpRequest.post("/")
                        .contentType(MediaType.parseMediaType(encoded.contentType()))
                        .body(Flux.just(toDataBuffer(encoded.bytes())));
                MockServerWebExchange exchange = MockServerWebExchange.from(request);

                byte[] extracted = invokeGetArgFromRequest(generated, instance, exchange);
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
                assertNoAdviceReferences(bytes);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                MockServerHttpRequest request = MockServerHttpRequest.post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new String(encoded.bytes(), UTF_8));
                MockServerWebExchange exchange = MockServerWebExchange.from(request);

                byte[] extracted = invokeGetArgFromRequest(generated, instance, exchange);
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
                assertNoAdviceReferences(bytes);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                MockServerHttpRequest request = MockServerHttpRequest.post("/")
                        .contentType(MediaType.APPLICATION_XML)
                        .body(new String(encoded.bytes(), UTF_8));
                MockServerWebExchange exchange = MockServerWebExchange.from(request);

                byte[] extracted = invokeGetArgFromRequest(generated, instance, exchange);
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
                assertNoAdviceReferences(bytes);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                MockServerHttpRequest request = MockServerHttpRequest.post("/")
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(new String(encoded.bytes(), UTF_8));
                MockServerWebExchange exchange = MockServerWebExchange.from(request);

                byte[] extracted = invokeGetArgFromRequest(generated, instance, exchange);
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
                assertNoAdviceReferences(bytes);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                MockServerHttpRequest request = MockServerHttpRequest.post("/")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(Flux.just(toDataBuffer(encoded.bytes())));
                MockServerWebExchange exchange = MockServerWebExchange.from(request);

                byte[] extracted = invokeGetArgFromRequest(generated, instance, exchange);
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

                MockServerHttpResponse response = new MockServerHttpResponse();
                invokeWrapResponse(generated, instance, response);

                assertEquals(HttpStatus.I_AM_A_TEAPOT, response.getStatusCode());
            }

            @Test
            @DisplayName("should set status code 201 for created resource")
            void shouldSetDifferentStatusCode() throws Exception {
                HttpProtocolConfig config = new HttpProtocolConfig();
                config.setResponseStatusCode(201);

                byte[] bytes = generateBytes(config);
                Class<?> generated = loadGeneratedClass(bytes);
                Object instance = generated.getDeclaredConstructor().newInstance();

                MockServerHttpResponse response = new MockServerHttpResponse();
                invokeWrapResponse(generated, instance, response);

                assertEquals(HttpStatus.CREATED, response.getStatusCode());
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

                MockServerHttpResponse response = new MockServerHttpResponse();
                invokeWrapResponse(generated, instance, response);

                assertEquals("value", response.getHeaders().getFirst("X-Custom"));
            }

            @Test
            @DisplayName("should set multiple headers")
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

                MockServerHttpResponse response = new MockServerHttpResponse();
                invokeWrapResponse(generated, instance, response);

                assertEquals("first-value", response.getHeaders().getFirst("X-First"));
                assertEquals("second-value", response.getHeaders().getFirst("X-Second"));
                assertEquals("application/json", response.getHeaders().getFirst("Content-Type"));
            }
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("should apply complete HTTP protocol config to Reactor WebFilter")
        void httpProtocolConfig_shouldBeAppliedToReactorWebFilter() throws Exception {
            HttpProtocolConfig http = new HttpProtocolConfig();
            http.setRequestBodyType(com.reajason.noone.server.profile.config.HttpRequestBodyType.TEXT);
            http.setRequestTemplate("pre{{payload}}suf");
            http.setResponseBodyType(HttpResponseBodyType.TEXT);
            http.setResponseTemplate("hello{{payload}}world");
            http.setResponseStatusCode(418);
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            responseHeaders.put("X-Test", "1");
            responseHeaders.put("Content-Type", "text/plain; charset=utf-8");
            http.setResponseHeaders(responseHeaders);

            byte[] bytes = generateBytes(http);
            assertNoAdviceReferences(bytes);
            Class<?> generated = loadGeneratedClass(bytes);
            Object instance = generated.getDeclaredConstructor().newInstance();

            // Test getArgFromRequest
            MockServerHttpRequest request = MockServerHttpRequest.post("/")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("prePAYLOADsuf");
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            byte[] extracted = invokeGetArgFromRequest(generated, instance, exchange);
            assertEquals("PAYLOAD", new String(extracted, UTF_8));

            // Test wrapResData
            byte[] wrappedData = invokeWrapResData(generated, instance, "PAYLOAD".getBytes(UTF_8));
            assertEquals("helloPAYLOADworld", new String(wrappedData, UTF_8));

            // Test wrapResponse
            MockServerHttpResponse response = new MockServerHttpResponse();
            invokeWrapResponse(generated, instance, response);

            assertEquals(HttpStatus.I_AM_A_TEAPOT, response.getStatusCode());
            assertEquals("1", response.getHeaders().getFirst("X-Test"));
            assertEquals("text/plain; charset=utf-8", response.getHeaders().getFirst("Content-Type"));
        }
    }

    // Helper methods for reflection-based invocation

    private static byte[] invokeGetArgFromRequest(Class<?> clazz, Object instance, ServerWebExchange exchange) throws Exception {
        Method method = clazz.getDeclaredMethod("getArgFromRequest", ServerWebExchange.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Mono<byte[]> mono = (Mono<byte[]>) method.invoke(instance, exchange);
        return mono.block();
    }

    private static byte[] invokeWrapResData(Class<?> clazz, Object instance, byte[] payload) throws Exception {
        Method method = clazz.getDeclaredMethod("wrapResData", byte[].class);
        method.setAccessible(true);
        return (byte[]) method.invoke(instance, payload);
    }

    private static void invokeWrapResponse(Class<?> clazz, Object instance, ServerHttpResponse response) throws Exception {
        Method method = clazz.getDeclaredMethod("wrapResponse", ServerHttpResponse.class);
        method.setAccessible(true);
        method.invoke(instance, response);
    }

    // Helper methods for test setup

    private static DataBuffer toDataBuffer(byte[] bytes) {
        return new DefaultDataBufferFactory().wrap(bytes);
    }

    private static byte[] generateBytes(HttpProtocolConfig http) {
        ShellConfig shellConfig = ShellConfig.builder()
                .server("Tomcat")
                .shellTool("Custom")
                .shellType(ShellType.SPRING_WEBFLUX_WEB_FILTER)
                .targetJreVersion(52)
                .build();

        Profile profile = new Profile();
        profile.setProtocolConfig(http);

        NoOneConfig noOneConfig = new NoOneConfig();
        noOneConfig.setShellClass(NoOneWebFilter.class);
        noOneConfig.setShellClassName("com.reajason.noone.test.GeneratedNoOneWebFilter");
        noOneConfig.setProfile(profile);

        NoOneMemShellGenerator generator = new NoOneMemShellGenerator(shellConfig, noOneConfig);
        return generator.getBytes();
    }

    private static void assertNoAdviceReferences(byte[] bytes) {
        String classFile = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(classFile.contains("com/reajason/noone/core/generator/protocol/reactor/ReactorGetArgFromRequestBodyAdvice"));
        assertFalse(classFile.contains("com/reajason/noone/core/generator/protocol/reactor/ReactorGetArgFromRequestFormUrlencodedAdvice"));
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

        return new DefiningClassLoader(NoOneWebFilter.class.getClassLoader()).define();
    }
}
