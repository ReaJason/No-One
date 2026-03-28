package com.reajason.noone.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DubboClientClasspathTest {
    @Test
    void apacheAndAlibabaClientsCanCoexist() {
        ApacheDubboClient apache = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService",
                DubboClientConfig.builder().build());
        AlibabaDubboClient alibaba = new AlibabaDubboClient(
                "dubbo://localhost:20880/com.example.TestService",
                DubboClientConfig.builder().build());
        assertNotNull(apache);
        assertNotNull(alibaba);
        assertInstanceOf(Client.class, apache);
        assertInstanceOf(Client.class, alibaba);
    }

    @Test
    void apacheClientClassLoadsCorrectly() {
        assertDoesNotThrow(() -> Class.forName("org.apache.dubbo.config.ReferenceConfig"));
    }

    @Test
    void alibabaClientClassLoadsCorrectly() {
        assertDoesNotThrow(() -> Class.forName("com.alibaba.dubbo.config.ReferenceConfig"));
    }
}
