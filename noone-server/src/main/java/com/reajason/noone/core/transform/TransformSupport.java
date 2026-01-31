package com.reajason.noone.core.transform;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

public final class TransformSupport {
    private static final int LZ4_HASH_LOG = 16;
    private static final int LZ4_HASH_SIZE = 1 << LZ4_HASH_LOG;
    private static final int LZ4_MIN_MATCH = 4;
    private static final int LZ4_MAX_DISTANCE = 0xFFFF;

    private TransformSupport() {
    }

    public static byte[] compress(byte[] input, CompressionAlgorithm algorithm) {
        if (algorithm == null) {
            throw new NullPointerException("algorithm");
        }
        byte[] data = input != null ? input : new byte[0];
        return switch (algorithm) {
            case NONE -> data;
            case GZIP -> gzipCompress(data);
            case DEFLATE -> deflateCompress(data);
            case LZ4 -> lz4Compress(data);
            default -> data;
        };
    }

    public static byte[] decompress(byte[] input, CompressionAlgorithm algorithm) {
        if (algorithm == null) {
            throw new NullPointerException("algorithm");
        }
        byte[] data = input != null ? input : new byte[0];
        return switch (algorithm) {
            case NONE -> data;
            case GZIP -> gzipDecompress(data);
            case DEFLATE -> deflateDecompress(data);
            case LZ4 -> lz4Decompress(data);
            default -> data;
        };
    }

    public static byte[] encrypt(byte[] input, EncryptionAlgorithm algorithm, String password) {
        if (algorithm == null) {
            throw new NullPointerException("algorithm");
        }
        byte[] data = input != null ? input : new byte[0];
        return switch (algorithm) {
            case NONE -> data;
            case XOR -> xor(data, deriveKey(password, "XOR", algorithm.keyLengthBytes()));
            case AES -> aesEncrypt(data, deriveKey(password, "AES", algorithm.keyLengthBytes()));
            case TRIPLE_DES -> tripleDesEncrypt(data, deriveKey(password, "TripleDES", algorithm.keyLengthBytes()));
            default -> data;
        };
    }

    public static byte[] decrypt(byte[] input, EncryptionAlgorithm algorithm, String password) {
        if (algorithm == null) {
            throw new NullPointerException("algorithm");
        }
        byte[] data = input != null ? input : new byte[0];
        return switch (algorithm) {
            case NONE -> data;
            case XOR -> xor(data, deriveKey(password, "XOR", algorithm.keyLengthBytes()));
            case AES -> aesDecrypt(data, deriveKey(password, "AES", algorithm.keyLengthBytes()));
            case TRIPLE_DES -> tripleDesDecrypt(data, deriveKey(password, "TripleDES", algorithm.keyLengthBytes()));
            default -> data;
        };
    }

    public static byte[] encode(byte[] input, EncodingAlgorithm algorithm) {
        if (algorithm == null) {
            throw new NullPointerException("algorithm");
        }
        byte[] data = input != null ? input : new byte[0];
        return switch (algorithm) {
            case NONE -> data;
            case BASE64 -> encodeBase64(data);
            case HEX -> encodeHex(data);
            case BIG_INTEGER -> encodeBigInteger(data);
            default -> data;
        };
    }

    public static byte[] decode(byte[] input, EncodingAlgorithm algorithm) {
        if (algorithm == null) {
            throw new NullPointerException("algorithm");
        }
        byte[] data = input != null ? input : new byte[0];
        return switch (algorithm) {
            case NONE -> data;
            case BASE64 -> decodeBase64(data);
            case HEX -> decodeHex(data);
            case BIG_INTEGER -> decodeBigInteger(data);
            default -> data;
        };
    }

    public static byte[] deriveKey(String password, String context, int lengthBytes) {
        if (lengthBytes <= 0) {
            return new byte[0];
        }
        if (password == null) {
            password = "";
        }
        if (context == null) {
            context = "";
        }
        byte[] seed = sha256(getBytes(password + ":" + context));
        if (seed.length == lengthBytes) {
            return seed;
        }
        if (seed.length > lengthBytes) {
            return Arrays.copyOf(seed, lengthBytes);
        }

        byte[] out = new byte[lengthBytes];
        int offset = 0;
        byte[] current = seed;
        while (offset < lengthBytes) {
            int toCopy = Math.min(current.length, lengthBytes - offset);
            System.arraycopy(current, 0, out, offset, toCopy);
            offset += toCopy;
            if (offset < lengthBytes) {
                current = sha256(current);
            }
        }
        return out;
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static byte[] getBytes(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (Exception e) {
            return s.getBytes();
        }
    }

    private static String newString(byte[] bytes) {
        try {
            return new String(bytes, "UTF-8");
        } catch (Exception e) {
            return new String(bytes);
        }
    }

    static byte[] xor(byte[] input, byte[] key) {
        byte[] data = input != null ? input : new byte[0];
        if (key == null || key.length == 0) {
            return data;
        }
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return out;
    }

    static byte[] aesEncrypt(byte[] plaintext, byte[] key) {
        return cipherEncrypt("AES", "AES/CBC/PKCS5Padding", 16, plaintext, key);
    }

    static byte[] aesDecrypt(byte[] ciphertextWithIv, byte[] key) {
        return cipherDecrypt("AES", "AES/CBC/PKCS5Padding", 16, ciphertextWithIv, key);
    }

    static byte[] tripleDesEncrypt(byte[] plaintext, byte[] key) {
        return cipherEncrypt("DESede", "DESede/CBC/PKCS5Padding", 8, plaintext, key);
    }

    static byte[] tripleDesDecrypt(byte[] ciphertextWithIv, byte[] key) {
        return cipherDecrypt("DESede", "DESede/CBC/PKCS5Padding", 8, ciphertextWithIv, key);
    }

    private static byte[] cipherEncrypt(
            String keyAlgorithm,
            String cipherAlgorithm,
            int ivLengthBytes,
            byte[] plaintext,
            byte[] keyBytes
    ) {
        if (plaintext == null) {
            plaintext = new byte[0];
        }
        if (keyBytes == null || keyBytes.length == 0) {
            throw new IllegalArgumentException("Key is required");
        }
        try {
            byte[] iv = new byte[ivLengthBytes];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(cipherAlgorithm);
            SecretKeySpec key = new SecretKeySpec(keyBytes, keyAlgorithm);
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Encrypt failed", e);
        }
    }

    private static byte[] cipherDecrypt(
            String keyAlgorithm,
            String cipherAlgorithm,
            int ivLengthBytes,
            byte[] ciphertextWithIv,
            byte[] keyBytes
    ) {
        if (ciphertextWithIv == null) {
            ciphertextWithIv = new byte[0];
        }
        if (keyBytes == null || keyBytes.length == 0) {
            throw new IllegalArgumentException("Key is required");
        }
        if (ciphertextWithIv.length < ivLengthBytes) {
            throw new IllegalArgumentException("Ciphertext too short");
        }
        try {
            byte[] iv = new byte[ivLengthBytes];
            System.arraycopy(ciphertextWithIv, 0, iv, 0, ivLengthBytes);

            int cipherLen = ciphertextWithIv.length - ivLengthBytes;
            byte[] encrypted = new byte[cipherLen];
            System.arraycopy(ciphertextWithIv, ivLengthBytes, encrypted, 0, cipherLen);

            Cipher cipher = Cipher.getInstance(cipherAlgorithm);
            SecretKeySpec key = new SecretKeySpec(keyBytes, keyAlgorithm);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Decrypt failed", e);
        }
    }

    static byte[] gzipCompress(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(input);
        } catch (IOException e) {
            throw new IllegalStateException("Gzip compress failed", e);
        }
        return out.toByteArray();
    }

    static byte[] gzipDecompress(byte[] input) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(input))) {
            return readAllBytes(gzip);
        } catch (IOException e) {
            throw new IllegalStateException("Gzip decompress failed", e);
        }
    }

    static byte[] deflateCompress(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(out)) {
            deflater.write(input);
        } catch (IOException e) {
            throw new IllegalStateException("Deflate compress failed", e);
        }
        return out.toByteArray();
    }

    static byte[] deflateDecompress(byte[] input) {
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(input))) {
            return readAllBytes(inflater);
        } catch (IOException e) {
            throw new IllegalStateException("Deflate decompress failed", e);
        }
    }

    private static byte[] readAllBytes(java.io.InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (n > 0) {
                out.write(buf, 0, n);
            }
        }
        return out.toByteArray();
    }

    static byte[] encodeHex(byte[] input) {
        char[] hex = new char[input.length * 2];
        int i = 0;
        for (byte b : input) {
            int v = b & 0xFF;
            hex[i++] = toHexChar(v >>> 4);
            hex[i++] = toHexChar(v & 0x0F);
        }
        return new String(hex).getBytes();
    }

    static byte[] decodeHex(byte[] input) {
        String s = newString(input).trim();
        String normalized = s.replaceAll("\\s+", "");
        if (normalized.isEmpty()) {
            return new byte[0];
        }
        if ((normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex content length must be even");
        }
        int len = normalized.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            int hi = Character.digit(normalized.charAt(i * 2), 16);
            int lo = Character.digit(normalized.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex character");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static char toHexChar(int value) {
        return (char) (value < 10 ? ('0' + value) : ('a' + (value - 10)));
    }

    static byte[] encodeBase64(byte[] input) {
        if (input == null || input.length == 0) {
            return new byte[0];
        }
        try {
            Class<?> base64Class = Class.forName("java.util.Base64");
            Object encoder = base64Class.getMethod("getEncoder").invoke(null);
            return (byte[]) encoder.getClass().getMethod("encode", byte[].class).invoke(encoder, input);
        } catch (Exception e) {
            try {
                Class<?> encoderClass = Class.forName("sun.misc.BASE64Encoder");
                Object encoder = encoderClass.newInstance();
                String encoded = (String) encoderClass.getMethod("encode", byte[].class).invoke(encoder, input);
                return encoded.replaceAll("\\s", "").getBytes();
            } catch (Exception ex) {
                throw new IllegalStateException("No Base64 encoder available", ex);
            }
        }
    }

    public static byte[] decodeBase64(byte[] input) {
        if (input == null || input.length == 0) {
            return new byte[0];
        }
        try {
            Class<?> base64Class = Class.forName("java.util.Base64");
            Object decoder = base64Class.getMethod("getDecoder").invoke(null);
            return (byte[]) decoder.getClass().getMethod("decode", byte[].class).invoke(decoder, input);
        } catch (Exception e) {
            try {
                Class<?> decoderClass = Class.forName("sun.misc.BASE64Decoder");
                Object decoder = decoderClass.newInstance();
                String inputStr = newString(input);
                return (byte[]) decoderClass.getMethod("decodeBuffer", String.class).invoke(decoder, inputStr);
            } catch (Exception ex) {
                throw new IllegalStateException("No Base64 decoder available", ex);
            }
        }
    }

    public static byte[] decodeBase64(String input) {
        if (input == null || input.isEmpty()) {
            return new byte[0];
        }
        return decodeBase64(getBytes(input));
    }

    public static byte[] encodeBigInteger(byte[] input) {
        byte[] withPrefix = new byte[input.length + 1];
        withPrefix[0] = 1;
        System.arraycopy(input, 0, withPrefix, 1, input.length);
        BigInteger big = new BigInteger(1, withPrefix);
        String s = big.toString(Character.MAX_RADIX);
        return getBytes(s);
    }

    public static byte[] decodeBigInteger(byte[] input) {
        String s = newString(input).trim();
        if (s.isEmpty()) {
            return new byte[0];
        }
        BigInteger big = new BigInteger(s, Character.MAX_RADIX);
        byte[] withPrefix = big.toByteArray();
        if (withPrefix.length == 0) {
            return new byte[0];
        }
        int offset = 0;
        if (withPrefix[0] == 0 && withPrefix.length > 1) {
            offset = 1;
        }
        if (withPrefix[offset] != 1) {
            throw new IllegalArgumentException("Invalid BigInteger payload");
        }
        int outLen = withPrefix.length - offset - 1;
        if (outLen <= 0) {
            return new byte[0];
        }
        byte[] out = new byte[outLen];
        System.arraycopy(withPrefix, offset + 1, out, 0, outLen);
        return out;
    }

    public static byte[] lz4Compress(byte[] input) {
        if (input == null || input.length == 0) {
            return new byte[0];
        }

        int[] table = new int[LZ4_HASH_SIZE];
        Arrays.fill(table, -1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int anchor = 0;
        int i = 0;
        int limit = input.length - LZ4_MIN_MATCH;
        while (i <= limit) {
            int h = lz4Hash(readIntLE(input, i));
            int ref = table[h];
            table[h] = i;

            if (ref < 0 || (i - ref) > LZ4_MAX_DISTANCE || !lz4Equals4(input, ref, i)) {
                i++;
                continue;
            }

            int matchLen = LZ4_MIN_MATCH;
            int max = input.length - i;
            while (matchLen < max && input[ref + matchLen] == input[i + matchLen]) {
                matchLen++;
            }

            int literalLen = i - anchor;
            int token = (Math.min(literalLen, 15) << 4) | Math.min(matchLen - LZ4_MIN_MATCH, 15);
            out.write(token);
            if (literalLen >= 15) {
                lz4WriteLen(out, literalLen - 15);
            }
            if (literalLen > 0) {
                out.write(input, anchor, literalLen);
            }

            int offset = i - ref;
            out.write(offset & 0xFF);
            out.write((offset >>> 8) & 0xFF);

            int matchExtra = matchLen - LZ4_MIN_MATCH;
            if (matchExtra >= 15) {
                lz4WriteLen(out, matchExtra - 15);
            }

            i += matchLen;
            anchor = i;
        }

        int lastLiterals = input.length - anchor;
        int token = Math.min(lastLiterals, 15) << 4;
        out.write(token);
        if (lastLiterals >= 15) {
            lz4WriteLen(out, lastLiterals - 15);
        }
        if (lastLiterals > 0) {
            out.write(input, anchor, lastLiterals);
        }

        return out.toByteArray();
    }

    public static byte[] lz4Decompress(byte[] input) {
        if (input == null || input.length == 0) {
            return new byte[0];
        }

        byte[] out = new byte[Math.max(64, input.length * 4)];
        int outPos = 0;

        int inPos = 0;
        while (inPos < input.length) {
            int token = input[inPos++] & 0xFF;
            int literalLen = token >>> 4;
            if (literalLen == 15) {
                int add;
                do {
                    if (inPos >= input.length) {
                        throw new IllegalArgumentException("Malformed LZ4 stream (literal length)");
                    }
                    add = input[inPos++] & 0xFF;
                    literalLen += add;
                } while (add == 255);
            }

            if (inPos + literalLen > input.length) {
                throw new IllegalArgumentException("Malformed LZ4 stream (literal bytes)");
            }
            out = ensureCapacity(out, outPos + literalLen);
            System.arraycopy(input, inPos, out, outPos, literalLen);
            inPos += literalLen;
            outPos += literalLen;

            if (inPos >= input.length) {
                break;
            }

            if (inPos + 2 > input.length) {
                throw new IllegalArgumentException("Malformed LZ4 stream (offset)");
            }
            int offset = (input[inPos++] & 0xFF) | ((input[inPos++] & 0xFF) << 8);
            if (offset <= 0 || offset > outPos) {
                throw new IllegalArgumentException("Malformed LZ4 stream (invalid offset)");
            }

            int matchLen = token & 0x0F;
            if (matchLen == 15) {
                int add;
                do {
                    if (inPos >= input.length) {
                        throw new IllegalArgumentException("Malformed LZ4 stream (match length)");
                    }
                    add = input[inPos++] & 0xFF;
                    matchLen += add;
                } while (add == 255);
            }
            matchLen += LZ4_MIN_MATCH;

            out = ensureCapacity(out, outPos + matchLen);
            int matchPos = outPos - offset;
            for (int i = 0; i < matchLen; i++) {
                out[outPos++] = out[matchPos + i];
            }
        }

        return Arrays.copyOf(out, outPos);
    }

    private static byte[] ensureCapacity(byte[] out, int needed) {
        if (out.length >= needed) {
            return out;
        }
        int next = out.length;
        while (next < needed) {
            next = next + (next >>> 1);
            if (next < 0) {
                next = needed;
                break;
            }
        }
        return Arrays.copyOf(out, next);
    }

    private static void lz4WriteLen(ByteArrayOutputStream out, int len) {
        int n = len;
        while (n >= 255) {
            out.write(255);
            n -= 255;
        }
        out.write(n);
    }

    private static int readIntLE(byte[] data, int index) {
        return (data[index] & 0xFF)
                | ((data[index + 1] & 0xFF) << 8)
                | ((data[index + 2] & 0xFF) << 16)
                | ((data[index + 3] & 0xFF) << 24);
    }

    private static int lz4Hash(int value) {
        return (value * -1640531535) >>> (32 - LZ4_HASH_LOG);
    }

    private static boolean lz4Equals4(byte[] data, int i, int j) {
        return data[i] == data[j]
                && data[i + 1] == data[j + 1]
                && data[i + 2] == data[j + 2]
                && data[i + 3] == data[j + 3];
    }
}
