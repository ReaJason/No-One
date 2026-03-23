package com.reajason.noone.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginIpPolicyServiceTest {

    @Test
    void allowsAnyIpWhenWhitelistIsEmpty() {
        LoginIpPolicyProperties properties = new LoginIpPolicyProperties();
        LoginIpPolicyService service = new LoginIpPolicyService(properties);

        assertTrue(service.isAllowed("127.0.0.1"));
        assertTrue(service.isAllowed("2001:db8:0:0:0:0:0:1"));
    }

    @Test
    void blocksIpThatIsNotInConfiguredWhitelist() {
        LoginIpPolicyProperties properties = new LoginIpPolicyProperties();
        properties.setLoginIpWhitelist(java.util.List.of("127.0.0.1"));
        LoginIpPolicyService service = new LoginIpPolicyService(properties);

        assertFalse(service.isAllowed("10.0.0.1"));
    }

    @Test
    void normalizesConfiguredIpv6Addresses() {
        LoginIpPolicyProperties properties = new LoginIpPolicyProperties();
        properties.setLoginIpWhitelist(java.util.List.of("2001:db8::1"));
        LoginIpPolicyService service = new LoginIpPolicyService(properties);

        assertTrue(service.isAllowed("2001:db8:0:0:0:0:0:1"));
    }

    @Test
    void rejectsInvalidConfiguredIpAddresses() {
        LoginIpPolicyProperties properties = new LoginIpPolicyProperties();
        properties.setLoginIpWhitelist(java.util.List.of("not-an-ip"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new LoginIpPolicyService(properties));
        assertEquals("IP地址不合法：not-an-ip", exception.getMessage());
    }
}
