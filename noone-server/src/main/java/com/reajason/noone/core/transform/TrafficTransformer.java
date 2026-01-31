package com.reajason.noone.core.transform;

import java.util.Objects;

public final class TrafficTransformer {
    private TrafficTransformer() {
    }

    public static byte[] outbound(byte[] input, TransformationSpec spec, String password) {
        Objects.requireNonNull(spec, "spec");
        byte[] data = input != null ? input : new byte[0];

        if (spec.compression() != CompressionAlgorithm.NONE) {
            data = TransformSupport.compress(data, spec.compression());
        }
        if (spec.encryption() != EncryptionAlgorithm.NONE) {
            data = TransformSupport.encrypt(data, spec.encryption(), password);
        }
        if (spec.encoding() != EncodingAlgorithm.NONE) {
            data = TransformSupport.encode(data, spec.encoding());
        }
        return data;
    }

    public static byte[] inbound(byte[] input, TransformationSpec spec, String password) {
        Objects.requireNonNull(spec, "spec");
        byte[] data = input != null ? input : new byte[0];

        if (spec.encoding() != EncodingAlgorithm.NONE) {
            data = TransformSupport.decode(data, spec.encoding());
        }
        if (spec.encryption() != EncryptionAlgorithm.NONE) {
            data = TransformSupport.decrypt(data, spec.encryption(), password);
        }
        if (spec.compression() != CompressionAlgorithm.NONE) {
            data = TransformSupport.decompress(data, spec.compression());
        }
        return data;
    }
}

