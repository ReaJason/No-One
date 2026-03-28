package com.reajason.noone.server.shell;

import com.reajason.noone.core.profile.config.ProtocolType;
import com.reajason.noone.server.profile.ProfileEntity;
import com.reajason.noone.server.profile.ProfileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class ShellConnectionPoolSignatureTest {

    private final ShellConnectionPool pool = new ShellConnectionPool(mock(ProfileMapper.class));

    @Test
    void shouldTreatLegacyAndClientConfigStorageAsSameEffectiveSignature() {
        ProfileEntity profile = profileEntity();

        Shell legacyShell = baseShell();
        legacyShell.setProxyUrl("http://proxy.example.com:8080");

        Shell clientConfigShell = baseShell();
        clientConfigShell.setClientConfig(Map.of("proxyUrl", "http://proxy.example.com:8080"));

        String legacySignature = ReflectionTestUtils.invokeMethod(pool, "signature", legacyShell, profile, null);
        String clientConfigSignature = ReflectionTestUtils.invokeMethod(pool, "signature", clientConfigShell, profile, null);

        assertEquals(legacySignature, clientConfigSignature);
    }

    @Test
    void shouldChangeSignatureWhenEffectiveClientConfigChanges() {
        ProfileEntity profile = profileEntity();

        Shell oldShell = baseShell();
        oldShell.setProxyUrl("http://proxy.example.com:8080");

        Shell newShell = baseShell();
        newShell.setClientConfig(Map.of("proxyUrl", "http://other-proxy.example.com:9090"));

        String oldSignature = ReflectionTestUtils.invokeMethod(pool, "signature", oldShell, profile, null);
        String newSignature = ReflectionTestUtils.invokeMethod(pool, "signature", newShell, profile, null);

        assertNotEquals(oldSignature, newSignature);
    }

    private Shell baseShell() {
        Shell shell = new Shell();
        shell.setUrl("http://target.example.com/api");
        shell.setLanguage(ShellLanguage.JAVA);
        shell.setStaging(false);
        shell.setShellType("SERVLET");
        shell.setProfileId(1L);
        return shell;
    }

    private ProfileEntity profileEntity() {
        ProfileEntity profile = new ProfileEntity();
        profile.setId(1L);
        profile.setProtocolType(ProtocolType.HTTP);
        profile.setUpdatedAt(LocalDateTime.of(2026, 3, 30, 12, 0));
        return profile;
    }
}
