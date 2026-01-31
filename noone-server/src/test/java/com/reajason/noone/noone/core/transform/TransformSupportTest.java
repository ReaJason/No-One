package com.reajason.noone.noone.core.transform;

import com.reajason.noone.core.transform.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TransformSupportTest {

    @Test
    void gzip_shouldRoundTrip() {
        byte[] input = "hello world".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = TransformSupport.compress(input, CompressionAlgorithm.GZIP);
        byte[] decompressed = TransformSupport.decompress(compressed, CompressionAlgorithm.GZIP);
        assertArrayEquals(input, decompressed);
    }

    @Test
    void deflate_shouldRoundTrip() {
        byte[] input = "hello world".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = TransformSupport.compress(input, CompressionAlgorithm.DEFLATE);
        byte[] decompressed = TransformSupport.decompress(compressed, CompressionAlgorithm.DEFLATE);
        assertArrayEquals(input, decompressed);
    }

    @Test
    void lz4_shouldRoundTrip_withLeadingZeros() {
        byte[] input = new byte[4096];
        new SecureRandom().nextBytes(input);
        input[0] = 0;
        input[1] = 0;
        input[2] = 1;

        byte[] compressed = TransformSupport.compress(input, CompressionAlgorithm.LZ4);
        byte[] decompressed = TransformSupport.decompress(compressed, CompressionAlgorithm.LZ4);
        assertArrayEquals(input, decompressed);
    }

    @Test
    void hex_shouldRoundTrip() {
        byte[] input = new byte[1024];
        new SecureRandom().nextBytes(input);
        byte[] encoded = TransformSupport.encode(input, EncodingAlgorithm.HEX);
        byte[] decoded = TransformSupport.decode(encoded, EncodingAlgorithm.HEX);
        assertArrayEquals(input, decoded);
    }

    @Test
    void bigInteger_shouldRoundTrip_withLeadingZeros() {
        byte[] input = new byte[128];
        new SecureRandom().nextBytes(input);
        input[0] = 0;
        input[1] = 0;
        input[2] = 0;

        byte[] encoded = TransformSupport.encode(input, EncodingAlgorithm.BIG_INTEGER);
        byte[] decoded = TransformSupport.decode(encoded, EncodingAlgorithm.BIG_INTEGER);
        assertArrayEquals(input, decoded);
    }

    @Test
    void encryption_shouldRoundTrip() {
        byte[] input = new byte[2048];
        new SecureRandom().nextBytes(input);
        String password = "secret";

        for (EncryptionAlgorithm algorithm : new EncryptionAlgorithm[]{EncryptionAlgorithm.XOR, EncryptionAlgorithm.AES, EncryptionAlgorithm.TRIPLE_DES}) {
            byte[] encrypted = TransformSupport.encrypt(input, algorithm, password);
            assertNotNull(encrypted);
            byte[] decrypted = TransformSupport.decrypt(encrypted, algorithm, password);
            assertArrayEquals(input, decrypted, "algorithm=" + algorithm);
        }
    }

    @Test
    void deriveKey_shouldBeDeterministic() {
        byte[] k1 = TransformSupport.deriveKey("secret", "AES", 16);
        byte[] k2 = TransformSupport.deriveKey("secret", "AES", 16);
        assertArrayEquals(k1, k2);
    }

    @Test
    void trafficTransformer_shouldRoundTrip() {
        TransformationSpec spec = new TransformationSpec(CompressionAlgorithm.GZIP, EncryptionAlgorithm.AES, EncodingAlgorithm.BASE64);
        byte[] input = "payload".getBytes(StandardCharsets.UTF_8);
        String password = "secret";

        byte[] outbound = TrafficTransformer.outbound(input, spec, password);
        byte[] inbound = TrafficTransformer.inbound(outbound, spec, password);
        assertArrayEquals(input, inbound);
    }
}

