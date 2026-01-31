package com.reajason.noone.core.transform;

import java.util.List;

public record TransformationSpec(
        CompressionAlgorithm compression,
        EncryptionAlgorithm encryption,
        EncodingAlgorithm encoding
) {
    public TransformationSpec {
        compression = compression != null ? compression : CompressionAlgorithm.NONE;
        encryption = encryption != null ? encryption : EncryptionAlgorithm.NONE;
        encoding = encoding != null ? encoding : EncodingAlgorithm.NONE;
    }

    public static TransformationSpec none() {
        return new TransformationSpec(CompressionAlgorithm.NONE, EncryptionAlgorithm.NONE, EncodingAlgorithm.NONE);
    }

    public static TransformationSpec parse(List<String> transformations) {
        if (transformations == null || transformations.isEmpty()) {
            return none();
        }

        // Enforce exactly 3 elements
        if (transformations.size() != 3) {
            throw new IllegalArgumentException("Transformations list must contain exactly 3 elements: [compress, encrypt, encode]");
        }

        // Positional parsing
        CompressionAlgorithm compression = tryParseCompression(safeGet(transformations, 0));
        EncryptionAlgorithm encryption = tryParseEncryption(safeGet(transformations, 1));
        EncodingAlgorithm encoding = tryParseEncoding(safeGet(transformations, 2));

        return new TransformationSpec(compression, encryption, encoding);
    }

    private static String safeGet(List<String> list, int index) {
        if (index < 0 || index >= list.size()) {
            return "";
        }
        String value = list.get(index);
        return value != null ? value : "";
    }

    private static CompressionAlgorithm tryParseCompression(String value) {
        if (value == null || value.isBlank() || "none".equalsIgnoreCase(value)) {
            return CompressionAlgorithm.NONE;
        }
        try {
            return CompressionAlgorithm.parse(value);
        } catch (IllegalArgumentException ignored) {
            return CompressionAlgorithm.NONE;
        }
    }

    private static EncryptionAlgorithm tryParseEncryption(String value) {
        if (value == null || value.isBlank() || "none".equalsIgnoreCase(value)) {
            return EncryptionAlgorithm.NONE;
        }
        try {
            return EncryptionAlgorithm.parse(value);
        } catch (IllegalArgumentException ignored) {
            return EncryptionAlgorithm.NONE;
        }
    }

    private static EncodingAlgorithm tryParseEncoding(String value) {
        if (value == null || value.isBlank() || "none".equalsIgnoreCase(value)) {
            return EncodingAlgorithm.NONE;
        }
        try {
            return EncodingAlgorithm.parse(value);
        } catch (IllegalArgumentException ignored) {
            return EncodingAlgorithm.NONE;
        }
    }
}

