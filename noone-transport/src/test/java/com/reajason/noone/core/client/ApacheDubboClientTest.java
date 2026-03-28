package com.reajason.noone.core.client;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApacheDubboClientTest {
    @Test
    void implementsClientInterface() {
        ApacheDubboClient client = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService",
                DubboClientConfig.builder().build());
        assertInstanceOf(Client.class, client);
    }

    @Test
    void testDubbo(){
        String url = "dubbo://127.0.0.1:20883/hello";
        ApacheDubboClient client = new ApacheDubboClient(url, DubboClientConfig.builder()
                .proxy(ProxyConfig.builder()
                        .type("SOCKS5")
                        .host("127.0.0.1")
                        .port(9999)
                        .build())
                .build());
        byte[] send = client.send("id".getBytes());
        System.out.println(new String(send));
    }

    @Test
    void getUrlReturnsConstructorValue() {
        String url = "dubbo://localhost:20880/com.example.TestService";
        ApacheDubboClient client = new ApacheDubboClient(url, DubboClientConfig.builder().build());
        assertEquals(url, client.getUrl());
    }

    @Test
    void notConnectedInitially() {
        ApacheDubboClient client = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService",
                DubboClientConfig.builder().build());
        assertFalse(client.isConnected());
    }

    @Test
    void nullConfigDefaultsToEmpty() {
        ApacheDubboClient client = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService", null);
        assertNotNull(client.getConfig());
    }

    @Test
    void buildParametersIncludesProxyWhenConfigured() {
        ProxyConfig proxy = ProxyConfig.builder()
                .type("SOCKS5").host("127.0.0.1").port(1080)
                .username("user").password("pass").build();
        DubboClientConfig config = DubboClientConfig.builder().proxy(proxy).build();
        ApacheDubboClient client = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService", config);
        java.util.Map<String, String> params = client.buildParameters();
        assertEquals("proxy-netty", params.get("transporter"));
        assertEquals("SOCKS5", params.get("proxy.type"));
        assertEquals("127.0.0.1", params.get("proxy.host"));
        assertEquals("1080", params.get("proxy.port"));
        assertEquals("user", params.get("proxy.username"));
        assertEquals("pass", params.get("proxy.password"));
    }

    @Test
    void buildParametersOmitsProxyWhenNotConfigured() {
        DubboClientConfig config = DubboClientConfig.builder().build();
        ApacheDubboClient client = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService", config);
        java.util.Map<String, String> params = client.buildParameters();
        assertNull(params.get("transporter"));
        assertNull(params.get("proxy.type"));
        assertEquals("false", params.get("reconnect"));
    }

    @Test
    void httpSendUsesJsonRpcTargetMethodInsteadOfGenericInvoke() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .setHeader("Content-Type", "application/json")
                    .body("{\"jsonrpc\":\"2.0\",\"result\":\"world\"}")
                    .build());
            server.start();

            DubboClientConfig config = DubboClientConfig.builder()
                    .interfaceName("io.github.reajason.dubbo.api.DemoService")
                    .methodName("sayHello")
                    .parameterTypes(new String[]{String.class.getName()})
                    .build();
            ApacheDubboClient client = new ApacheDubboClient(
                    server.url("/io.github.reajason.dubbo.api.DemoService").toString(),
                    config);

            byte[] response = client.send("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertEquals("world", new String(response, java.nio.charset.StandardCharsets.UTF_8));
            RecordedRequest request = server.takeRequest();
            assertNotNull(request);
            assertEquals("/io.github.reajason.dubbo.api.DemoService", request.getTarget());
            String body = request.getBody().utf8();
            assertTrue(body.contains("\"method\":\"sayHello\""));
            assertTrue(body.contains("\"params\":[\"hello\"]"));
            assertFalse(body.contains("$invoke"));
            assertFalse(request.getTarget().contains("generic"));
        }
    }

    @Test
    void httpSendUsesJsonRpcBase64ForDefaultByteArrayArgument() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .setHeader("Content-Type", "application/json")
                    .body("{\"jsonrpc\":\"2.0\",\"result\":\"d29ybGQ=\"}")
                    .build());
            server.start();

            ApacheDubboClient client = new ApacheDubboClient(
                    server.url("/io.github.reajason.dubbo.api.DemoService").toString(),
                    DubboClientConfig.builder()
                            .interfaceName("io.github.reajason.dubbo.api.DemoService")
                            .methodName("handle")
                            .build());

            byte[] response = client.send("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertEquals("world", new String(response, java.nio.charset.StandardCharsets.UTF_8));
            RecordedRequest request = server.takeRequest();
            assertNotNull(request);
            String body = request.getBody().utf8();
            assertTrue(body.contains("\"method\":\"handle\""));
            assertTrue(body.contains("\"params\":[\"aGVsbG8=\"]"));
        }
    }

    @Test
    void httpSendRetriesJsonRpcGenericInvocationThroughClientReconnect() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder()
                    .code(500)
                    .body("temporary failure")
                    .build());
            server.enqueue(new MockResponse.Builder()
                    .setHeader("Content-Type", "application/json")
                    .body("{\"jsonrpc\":\"2.0\",\"result\":\"world\"}")
                    .build());
            server.start();

            ApacheDubboClient client = new ApacheDubboClient(
                    server.url("/io.github.reajason.dubbo.api.DemoService").toString(),
                    DubboClientConfig.builder()
                            .interfaceName("io.github.reajason.dubbo.api.DemoService")
                            .methodName("sayHello")
                            .parameterTypes(new String[]{String.class.getName()})
                            .build());

            byte[] response = client.send("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertEquals("world", new String(response, java.nio.charset.StandardCharsets.UTF_8));
            assertNotNull(server.takeRequest());
            RecordedRequest retriedRequest = server.takeRequest();
            assertNotNull(retriedRequest);
            assertEquals("/io.github.reajason.dubbo.api.DemoService", retriedRequest.getTarget());
            assertTrue(retriedRequest.getBody().utf8().contains("\"method\":\"sayHello\""));
        }
    }


    @Test
    @Disabled
    void test() {
        DubboClientConfig build = DubboClientConfig.builder()
                .interfaceName("io.github.reajason.dubbo.api.DemoService")
                .methodName("sayHello")
                .proxy(ProxyConfig.builder().type("SOCKS5").host("192.168.31.206").port(9999).build())
                .parameterTypes(new String[]{String.class.getName()})
                .build();
        ApacheDubboClient client = new ApacheDubboClient(
                "http://127.0.0.1:28081/io.github.reajason.dubbo.api.DemoService",
//                "hessian://127.0.0.1:28080/io.github.reajason.dubbo.api.DemoService",
//                "dubbo://127.0.0.1:20880/io.github.reajason.dubbo.api.DemoService",
//                "tri://127.0.0.1:50051/io.github.reajason.dubbo.api.DemoService",
                build
        );
        byte[] send = client.send("fuck".getBytes());
        System.out.println(new String(send));
    }
}
