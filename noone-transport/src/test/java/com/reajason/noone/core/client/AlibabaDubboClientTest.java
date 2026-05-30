package com.reajason.noone.core.client;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlibabaDubboClientTest {
    @Test
    void implementsClientInterface() {
        AlibabaDubboClient client = new AlibabaDubboClient(
                "dubbo://localhost:20880/com.example.TestService",
                DubboClientConfig.builder().build());
        assertInstanceOf(Client.class, client);
    }

    @Test
    void testDubbo(){
        String url = "http://192.168.31.206:28081/org.apache.http.web.handlers.KcepD.IAuthAlibabaDubboService";
        AlibabaDubboClient client = new AlibabaDubboClient(url,
                DubboClientConfig.builder()
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
        AlibabaDubboClient client = new AlibabaDubboClient(url, DubboClientConfig.builder().build());
        assertEquals(url, client.getUrl());
    }

    @Test
    void notConnectedInitially() {
        AlibabaDubboClient client = new AlibabaDubboClient(
                "dubbo://localhost:20880/com.example.TestService",
                DubboClientConfig.builder().build());
        assertFalse(client.isConnected());
    }

    @Test
    void nullConfigDefaultsToEmpty() {
        AlibabaDubboClient client = new AlibabaDubboClient(
                "dubbo://localhost:20880/com.example.TestService", null);
        assertNotNull(client.getConfig());
    }

    @Test
    void buildParametersIncludesNettyIsolationForDubboProxy() {
        ProxyConfig proxy = ProxyConfig.builder()
                .type("SOCKS5").host("127.0.0.1").port(1080)
                .username("user").password("pass").build();
        DubboClientConfig config = DubboClientConfig.builder().proxy(proxy).build();
        AlibabaDubboClient client = new AlibabaDubboClient(
                "dubbo://localhost:20880/com.example.TestService", config);
        java.util.Map<String, String> params = client.buildParameters();
        assertEquals("1", params.get("connections"));
        assertEquals("proxy-netty", params.get("client"));
        assertEquals("proxy-netty", params.get("transporter"));
        assertProxyParams(params);
    }

    @Test
    void buildParametersKeepsOnlyProxyParamsForHttpProxy() {
        ProxyConfig proxy = ProxyConfig.builder()
                .type("SOCKS5").host("127.0.0.1").port(1080)
                .username("user").password("pass").build();
        DubboClientConfig config = DubboClientConfig.builder().proxy(proxy).build();
        AlibabaDubboClient client = new AlibabaDubboClient(
                "http://localhost:8080/com.example.TestService", config);
        java.util.Map<String, String> params = client.buildParameters();
        assertNull(params.get("connections"));
        assertNull(params.get("client"));
        assertNull(params.get("transporter"));
        assertProxyParams(params);
    }

    @Test
    void buildParametersKeepsOnlyProxyParamsForHessianProxy() {
        ProxyConfig proxy = ProxyConfig.builder()
                .type("SOCKS5").host("127.0.0.1").port(1080)
                .username("user").password("pass").build();
        DubboClientConfig config = DubboClientConfig.builder().proxy(proxy).build();
        AlibabaDubboClient client = new AlibabaDubboClient(
                "hessian://localhost:28080/com.example.TestService", config);
        java.util.Map<String, String> params = client.buildParameters();
        assertNull(params.get("connections"));
        assertNull(params.get("client"));
        assertNull(params.get("transporter"));
        assertProxyParams(params);
    }

    private void assertProxyParams(java.util.Map<String, String> params) {
        assertEquals("SOCKS5", params.get("proxy.type"));
        assertEquals("127.0.0.1", params.get("proxy.host"));
        assertEquals("1080", params.get("proxy.port"));
        assertEquals("user", params.get("proxy.username"));
        assertEquals("pass", params.get("proxy.password"));
    }

    @Test
    void buildParametersOmitsProxyWhenNotConfigured() {
        DubboClientConfig config = DubboClientConfig.builder().build();
        AlibabaDubboClient client = new AlibabaDubboClient(
                "dubbo://localhost:20880/com.example.TestService", config);
        java.util.Map<String, String> params = client.buildParameters();
        assertNull(params.get("connections"));
        assertNull(params.get("client"));
        assertNull(params.get("transporter"));
        assertEquals("false", params.get("reconnect"));
    }

    @Test
    @Disabled
    void test(){
        DubboClientConfig build = DubboClientConfig.builder()
                .interfaceName("io.github.reajason.dubbo.api.DemoService")
                .methodName("sayHello")
                .proxy(ProxyConfig.builder().type("socks5").host("192.168.31.206").port(9999).build())
                .parameterTypes(new String[]{String.class.getName()})
                .build();
        AlibabaDubboClient client = new AlibabaDubboClient(
                "http://127.0.0.1:28081/io.github.reajason.dubbo.api.DemoService",
//                "hessian://127.0.0.1:28080/io.github.reajason.dubbo.api.DemoService",
//                "dubbo://127.0.0.1:20880/io.github.reajason.dubbo.api.DemoService",
                build
        );
        byte[] send = client.send("fuck".getBytes());
        System.out.println(new String(send));
    }
}
