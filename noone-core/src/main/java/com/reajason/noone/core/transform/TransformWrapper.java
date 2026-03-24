package com.reajason.noone.core.transform;

import com.reajason.noone.core.profile.Profile;
import net.bytebuddy.dynamic.DynamicType;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class TransformWrapper {
    public static DynamicType.Builder<?> extend(DynamicType.Builder<?> builder, Profile profile) {
        TransformationSpec request = TransformationSpec.parse(profile.getRequestTransformations());
        TransformationSpec response = TransformationSpec.parse(profile.getResponseTransformations());

        boolean requestEnabled = !isNone(request);
        boolean responseEnabled = !isNone(response);
        if (!requestEnabled && !responseEnabled) {
            return builder;
        }

        String password = profile.getPassword();
        String requestKeyBase64 = keyBase64(password, request.encryption());
        String responseKeyBase64 = keyBase64(password, response.encryption());

        Set<MethodSig> methods = new HashSet<>();
        if (requestEnabled) {
            collectRequiredMethods(methods, request, TransformDirection.INBOUND);
        }
        if (responseEnabled) {
            collectRequiredMethods(methods, response, TransformDirection.OUTBOUND);
        }

        if (requestEnabled) {
            builder = builder.method(named("transformReqPayload")
                            .and(takesArguments(byte[].class))
                            .and(returns(byte[].class)))
                    .intercept(new TransformPayloadImplementation(
                            TransformDirection.INBOUND,
                            request,
                            requestKeyBase64
                    ));
        }

        if (responseEnabled) {
            builder = builder.method(named("transformResData")
                            .and(takesArguments(byte[].class))
                            .and(returns(byte[].class)))
                    .intercept(new TransformPayloadImplementation(
                            TransformDirection.OUTBOUND,
                            response,
                            responseKeyBase64
                    ));
        }

        // Inject helper methods using ByteBuddy visitor
        if (!methods.isEmpty()) {
            builder = builder.visit(new MethodInjectionVisitor(TransformSupport.class, methods));
        }

        return builder;
    }

    private static boolean isNone(TransformationSpec spec) {
        if (spec == null) {
            return true;
        }
        return spec.compression() == CompressionAlgorithm.NONE
                && spec.encryption() == EncryptionAlgorithm.NONE
                && spec.encoding() == EncodingAlgorithm.NONE;
    }

    private static String keyBase64(String password, EncryptionAlgorithm algorithm) {
        if (algorithm == null || algorithm == EncryptionAlgorithm.NONE) {
            return "";
        }
        String context = switch (algorithm) {
            case XOR -> "XOR";
            case AES -> "AES";
            case TRIPLE_DES -> "TripleDES";
            case NONE -> "";
        };
        byte[] key = TransformSupport.deriveKey(password, context, algorithm.keyLengthBytes());
        return Base64.getEncoder().encodeToString(key);
    }

    private static void collectRequiredMethods(Set<MethodSig> out, TransformationSpec spec, TransformDirection direction) {
        CompressionAlgorithm compression = spec.compression();
        if (compression != null && compression != CompressionAlgorithm.NONE) {
            switch (compression) {
                case GZIP ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "gzipDecompress" : "gzipCompress", "([B)[B"));
                case DEFLATE ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "deflateDecompress" : "deflateCompress", "([B)[B"));
                case LZ4 ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "lz4Decompress" : "lz4Compress", "([B)[B"));
            }
        }

        EncryptionAlgorithm encryption = spec.encryption();
        if (encryption != null && encryption != EncryptionAlgorithm.NONE) {
            switch (encryption) {
                case XOR -> out.add(new MethodSig("xor", "([B[B)[B"));
                case AES ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "aesDecrypt" : "aesEncrypt", "([B[B)[B"));
                case TRIPLE_DES ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "tripleDesDecrypt" : "tripleDesEncrypt", "([B[B)[B"));
            }
        }

        EncodingAlgorithm encoding = spec.encoding();
        if (encoding != null && encoding != EncodingAlgorithm.NONE) {
            switch (encoding) {
                case HEX ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "decodeHex" : "encodeHex", "([B)[B"));
                case BIG_INTEGER ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "decodeBigInteger" : "encodeBigInteger", "([B)[B"));
                case BASE64 -> {
                    out.add(new MethodSig(direction == TransformDirection.INBOUND ? "decodeBase64" : "encodeBase64", "([B)[B"));
                }
            }
        }
    }
}
