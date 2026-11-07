package com.reajason.noone.core.client;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Base64;

/**
 * Loads the client certificate used for mutual TLS.
 *
 * <p>The certificate is stored as a Base64 encoded PKCS#12 keystore, which is the Java-native way
 * to carry a certificate chain and its private key in one password-protected blob. MIME decoding is
 * used so a value wrapped across lines is accepted as well as one long line.</p>
 *
 * @author ReaJason
 */
final class ClientCertificateSupport {

    private static final String KEYSTORE_TYPE = "PKCS12";

    private ClientCertificateSupport() {
    }

    /**
     * @param certificateBase64 the Base64 PKCS#12 keystore, may be {@code null} or blank
     * @param password          the keystore and key password, may be {@code null} or empty
     * @return the key managers to hand to an SSL context, or {@code null} when no certificate is set
     * @throws IllegalArgumentException when the value is not Base64, is not a PKCS#12 keystore, or
     *                                  when a password was configured without a certificate
     */
    static KeyManager[] loadKeyManagers(String certificateBase64, String password) {
        boolean hasCertificate = certificateBase64 != null && !certificateBase64.trim().isEmpty();
        if (!hasCertificate) {
            if (password != null && !password.isEmpty()) {
                throw new IllegalArgumentException(
                        "clientCertificatePassword was configured without clientCertificate");
            }
            return null;
        }

        byte[] keystoreBytes;
        try {
            keystoreBytes = Base64.getMimeDecoder().decode(certificateBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("clientCertificate is not valid Base64", e);
        }

        char[] passwordChars = password == null ? new char[0] : password.toCharArray();
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            keyStore.load(new ByteArrayInputStream(keystoreBytes), passwordChars);

            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore, passwordChars);
            return factory.getKeyManagers();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to load clientCertificate as a PKCS#12 keystore: " + e.getMessage(), e);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }
}
