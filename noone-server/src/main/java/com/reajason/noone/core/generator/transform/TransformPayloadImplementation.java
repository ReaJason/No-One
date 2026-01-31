package com.reajason.noone.core.generator.transform;

import com.reajason.noone.core.transform.CompressionAlgorithm;
import com.reajason.noone.core.transform.EncodingAlgorithm;
import com.reajason.noone.core.transform.EncryptionAlgorithm;
import com.reajason.noone.core.transform.TransformationSpec;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

import java.util.Objects;

public final class TransformPayloadImplementation implements Implementation {

    private final TransformDirection direction;
    private final CompressionAlgorithm compression;
    private final EncryptionAlgorithm encryption;
    private final EncodingAlgorithm encoding;
    private final String keyBase64;

    public TransformPayloadImplementation(
            TransformDirection direction,
            TransformationSpec spec,
            String keyBase64
    ) {
        this.direction = Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(spec, "spec");
        this.compression = spec.compression();
        this.encryption = spec.encryption();
        this.encoding = spec.encoding();
        this.keyBase64 = keyBase64 != null ? keyBase64 : "";
    }

    @Override
    public InstrumentedType prepare(InstrumentedType instrumentedType) {
        return instrumentedType;
    }

    @Override
    public ByteCodeAppender appender(Target implementationTarget) {
        return new TransformAppender(direction, compression, encryption, encoding, keyBase64);
    }

    private record TransformAppender(
            TransformDirection direction,
            CompressionAlgorithm compression,
            EncryptionAlgorithm encryption,
            EncodingAlgorithm encoding,
            String keyBase64
    ) implements ByteCodeAppender, Opcodes {
        @Override
        public Size apply(MethodVisitor mv, Context context, MethodDescription method) {
            String owner = method.getDeclaringType().asErasure().getInternalName();

            int baseLocal = method.getStackSize(); // this + arg
            int dataIndex = baseLocal;
            int keyIndex = baseLocal + 1;

            Label nonNull = new Label();
            mv.visitVarInsn(ALOAD, 1);
            mv.visitJumpInsn(IFNONNULL, nonNull);
            mv.visitInsn(ICONST_0);
            mv.visitIntInsn(NEWARRAY, T_BYTE);
            mv.visitInsn(ARETURN);
            mv.visitLabel(nonNull);

            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ASTORE, dataIndex);

            boolean needsKey = encryption != null && encryption != EncryptionAlgorithm.NONE;
            if (needsKey) {
                emitDecodeKeyBase64(mv, owner, keyBase64, keyIndex);
            }

            if (direction == TransformDirection.INBOUND) {
                emitDecode(mv, owner, dataIndex);
                emitDecrypt(mv, owner, dataIndex, keyIndex);
                emitDecompress(mv, owner, dataIndex);
            } else {
                emitCompress(mv, owner, dataIndex);
                emitEncrypt(mv, owner, dataIndex, keyIndex);
                emitEncode(mv, owner, dataIndex);
            }

            mv.visitVarInsn(ALOAD, dataIndex);
            mv.visitInsn(ARETURN);

            int extraLocals = needsKey ? 2 : 1;
            return new Size(8, method.getStackSize() + extraLocals);
        }

        private void emitDecodeKeyBase64(MethodVisitor mv, String owner, String keyBase64, int keyIndex) {
            mv.visitLdcInsn(keyBase64);
            mv.visitMethodInsn(INVOKESTATIC, owner, "decodeBase64", "(Ljava/lang/String;)[B", false);
            mv.visitVarInsn(ASTORE, keyIndex);
        }

        private void emitDecode(MethodVisitor mv, String owner, int dataIndex) {
            if (encoding == null || encoding == EncodingAlgorithm.NONE) {
                return;
            }
            switch (encoding) {
                case BASE64 -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "decodeBase64", "([B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                case HEX -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "decodeHex", "([B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                case BIG_INTEGER -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "decodeBigInteger", "([B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                default -> {
                }
            }
        }

        private void emitEncode(MethodVisitor mv, String owner, int dataIndex) {
            if (encoding == null || encoding == EncodingAlgorithm.NONE) {
                return;
            }
            switch (encoding) {
                case BASE64 -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "encodeBase64", "([B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                case HEX -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "encodeHex", "([B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                case BIG_INTEGER -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "encodeBigInteger", "([B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                default -> {
                }
            }
        }

        private void emitDecrypt(MethodVisitor mv, String owner, int dataIndex, int keyIndex) {
            if (encryption == null || encryption == EncryptionAlgorithm.NONE) {
                return;
            }
            switch (encryption) {
                case XOR -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitVarInsn(ALOAD, keyIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "xor", "([B[B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                case AES -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitVarInsn(ALOAD, keyIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "aesDecrypt", "([B[B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                case TRIPLE_DES -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitVarInsn(ALOAD, keyIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "tripleDesDecrypt", "([B[B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                default -> {
                }
            }
        }

        private void emitEncrypt(MethodVisitor mv, String owner, int dataIndex, int keyIndex) {
            if (encryption == null || encryption == EncryptionAlgorithm.NONE) {
                return;
            }
            switch (encryption) {
                case XOR -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitVarInsn(ALOAD, keyIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "xor", "([B[B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                case AES -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitVarInsn(ALOAD, keyIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "aesEncrypt", "([B[B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                case TRIPLE_DES -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitVarInsn(ALOAD, keyIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "tripleDesEncrypt", "([B[B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                default -> {
                }
            }
        }

        private void emitDecompress(MethodVisitor mv, String owner, int dataIndex) {
            if (compression == null || compression == CompressionAlgorithm.NONE) {
                return;
            }
            switch (compression) {
                case GZIP -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "gzipDecompress", "([B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                case DEFLATE -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "deflateDecompress", "([B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                case LZ4 -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "lz4Decompress", "([B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                default -> {
                }
            }
        }

        private void emitCompress(MethodVisitor mv, String owner, int dataIndex) {
            if (compression == null || compression == CompressionAlgorithm.NONE) {
                return;
            }
            switch (compression) {
                case GZIP -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "gzipCompress", "([B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                case DEFLATE -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "deflateCompress", "([B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                case LZ4 -> {
                    mv.visitVarInsn(ALOAD, dataIndex);
                    mv.visitMethodInsn(INVOKESTATIC, owner, "lz4Compress", "([B)[B", false);
                    mv.visitVarInsn(ASTORE, dataIndex);
                }
                default -> {
                }
            }
        }
    }
}
