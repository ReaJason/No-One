package com.reajason.noone.core.client;

import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManager;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCertificateSupportTest {

    private static final String PASSWORD = "test-password";

    @Test
    void shouldReturnNoKeyManagersWhenNoCertificateIsConfigured() {
        assertNull(ClientCertificateSupport.loadKeyManagers(null, null));
        assertNull(ClientCertificateSupport.loadKeyManagers("", ""));
        assertNull(ClientCertificateSupport.loadKeyManagers("   ", null));
    }

    @Test
    void shouldRejectAPasswordWithoutACertificate() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ClientCertificateSupport.loadKeyManagers(null, PASSWORD));

        assertTrue(exception.getMessage().contains("clientCertificatePassword"));
        assertTrue(exception.getMessage().contains("clientCertificate"));
    }

    @Test
    void shouldRejectInvalidBase64() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ClientCertificateSupport.loadKeyManagers("not base64 !!!", PASSWORD));

        assertTrue(exception.getMessage().contains("Base64"));
    }

    @Test
    void shouldRejectBase64ThatIsNotAPkcs12Keystore() {
        String garbage = Base64.getEncoder().encodeToString("not a keystore".getBytes());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ClientCertificateSupport.loadKeyManagers(garbage, PASSWORD));

        assertTrue(exception.getMessage().contains("PKCS#12"));
    }

    @Test
    void shouldRejectTheWrongKeystorePassword() throws Exception {
        String keystore = clientKeystore();

        assertThrows(
                IllegalArgumentException.class,
                () -> ClientCertificateSupport.loadKeyManagers(keystore, "wrong-password"));
    }

    @Test
    void shouldLoadKeyManagersFromAPkcs12Keystore() throws Exception {
        KeyManager[] keyManagers = ClientCertificateSupport.loadKeyManagers(clientKeystore(), PASSWORD);

        assertNotNull(keyManagers);
        assertTrue(keyManagers.length > 0);
    }

    @Test
    void shouldAcceptALineWrappedKeystore() throws Exception {
        String wrapped = Base64.getMimeEncoder(32, new byte[]{'\n'})
                .encodeToString(Base64.getDecoder().decode(clientKeystore()));

        KeyManager[] keyManagers = ClientCertificateSupport.loadKeyManagers(wrapped, PASSWORD);

        assertNotNull(keyManagers);
        assertEquals(true, keyManagers.length > 0);
    }

    private String clientKeystore() throws Exception {
        KeyPair keyPair = TestCertificates.rsaKeyPair();
        X509Certificate certificate = TestCertificates.selfSigned(keyPair, "client");
        return TestCertificates.pkcs12Base64(keyPair, certificate, PASSWORD);
    }
}
