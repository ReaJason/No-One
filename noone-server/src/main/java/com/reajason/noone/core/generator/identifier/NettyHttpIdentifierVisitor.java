package com.reajason.noone.core.generator.identifier;

import com.reajason.noone.server.profile.config.IdentifierConfig;
import com.reajason.noone.server.profile.config.IdentifierLocation;
import com.reajason.noone.server.profile.config.IdentifierOperator;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

import java.util.Objects;

/**
 * Identifier visitor for Netty HttpRequest.
 * Supports header, query parameter, and cookie based authentication.
 */
public final class NettyHttpIdentifierVisitor implements Implementation {
    private final IdentifierLocation location;
    private final IdentifierOperator operator;
    private final String name;
    private final String value;

    public NettyHttpIdentifierVisitor(IdentifierConfig identifier) {
        Objects.requireNonNull(identifier, "identifier");
        this.location = Objects.requireNonNull(identifier.getLocation(), "identifier.location");
        this.operator = Objects.requireNonNull(identifier.getOperator(), "identifier.operator");
        this.name = Objects.requireNonNull(identifier.getName(), "identifier.name");
        this.value = Objects.requireNonNull(identifier.getValue(), "identifier.value");
    }

    @Override
    public InstrumentedType prepare(InstrumentedType instrumentedType) {
        return instrumentedType;
    }

    @Override
    public ByteCodeAppender appender(Target implementationTarget) {
        return new IdentifierAppender(location, operator, name, value);
    }

    private record IdentifierAppender(IdentifierLocation location, IdentifierOperator operator, String name,
                                      String value) implements ByteCodeAppender, Opcodes {
        private static final String HTTP_REQUEST = "io/netty/handler/codec/http/HttpRequest";
        private static final String HTTP_HEADERS = "io/netty/handler/codec/http/HttpHeaders";
        private static final String QUERY_STRING_DECODER = "io/netty/handler/codec/http/QueryStringDecoder";
        private static final String SERVER_COOKIE_DECODER = "io/netty/handler/codec/http/cookie/ServerCookieDecoder";
        private static final String NETTY_COOKIE = "io/netty/handler/codec/http/cookie/Cookie";
        private static final String JAVA_LANG_STRING = "java/lang/String";
        private static final String JAVA_UTIL_MAP = "java/util/Map";
        private static final String JAVA_UTIL_LIST = "java/util/List";
        private static final String JAVA_UTIL_SET = "java/util/Set";
        private static final String JAVA_UTIL_ITERATOR = "java/util/Iterator";

        @Override
        public Size apply(MethodVisitor methodVisitor, Context context, MethodDescription methodDescription) {
            LocationKind locationKind = LocationKind.from(location);

            int baseLocalIndex = methodDescription.getStackSize();
            switch (locationKind) {
                case HEADER -> emitHeader(methodVisitor);
                case PARAMETER -> emitParameter(methodVisitor, baseLocalIndex);
                case COOKIE -> emitCookie(methodVisitor, baseLocalIndex);
            }

            return new Size(10, methodDescription.getStackSize() + locationKind.localSlots);
        }

        private void emitHeader(MethodVisitor mv) {
            Label returnFalse = new Label();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, HTTP_REQUEST, "headers",
                    "()Lio/netty/handler/codec/http/HttpHeaders;", true);
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(INVOKEVIRTUAL, HTTP_HEADERS, "get",
                    "(Ljava/lang/CharSequence;)Ljava/lang/String;", false);
            mv.visitJumpInsn(IFNULL, returnFalse);

            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, HTTP_REQUEST, "headers",
                    "()Lio/netty/handler/codec/http/HttpHeaders;", true);
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(INVOKEVIRTUAL, HTTP_HEADERS, "get",
                    "(Ljava/lang/CharSequence;)Ljava/lang/String;", false);
            mv.visitLdcInsn(value);
            emitStringOperator(mv);
            mv.visitInsn(IRETURN);

            mv.visitLabel(returnFalse);
            mv.visitInsn(ICONST_0);
            mv.visitInsn(IRETURN);
        }

        private void emitParameter(MethodVisitor mv, int baseLocalIndex) {
            int decoderIndex = baseLocalIndex;
            int paramsIndex = baseLocalIndex + 1;
            int listIndex = baseLocalIndex + 2;
            int paramValueIndex = baseLocalIndex + 3;

            Label returnFalse = new Label();

            mv.visitTypeInsn(NEW, QUERY_STRING_DECODER);
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, HTTP_REQUEST, "uri", "()Ljava/lang/String;", true);
            mv.visitMethodInsn(INVOKESPECIAL, QUERY_STRING_DECODER, "<init>", "(Ljava/lang/String;)V", false);
            mv.visitVarInsn(ASTORE, decoderIndex);

            mv.visitVarInsn(ALOAD, decoderIndex);
            mv.visitMethodInsn(INVOKEVIRTUAL, QUERY_STRING_DECODER, "parameters", "()Ljava/util/Map;", false);
            mv.visitVarInsn(ASTORE, paramsIndex);

            mv.visitVarInsn(ALOAD, paramsIndex);
            mv.visitJumpInsn(IFNULL, returnFalse);

            mv.visitVarInsn(ALOAD, paramsIndex);
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(INVOKEINTERFACE, JAVA_UTIL_MAP, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, JAVA_UTIL_LIST);
            mv.visitVarInsn(ASTORE, listIndex);

            mv.visitVarInsn(ALOAD, listIndex);
            mv.visitJumpInsn(IFNULL, returnFalse);

            mv.visitVarInsn(ALOAD, listIndex);
            mv.visitMethodInsn(INVOKEINTERFACE, JAVA_UTIL_LIST, "isEmpty", "()Z", true);
            mv.visitJumpInsn(IFNE, returnFalse);

            mv.visitVarInsn(ALOAD, listIndex);
            mv.visitInsn(ICONST_0);
            mv.visitMethodInsn(INVOKEINTERFACE, JAVA_UTIL_LIST, "get", "(I)Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, JAVA_LANG_STRING);
            mv.visitVarInsn(ASTORE, paramValueIndex);

            mv.visitVarInsn(ALOAD, paramValueIndex);
            mv.visitJumpInsn(IFNULL, returnFalse);

            mv.visitVarInsn(ALOAD, paramValueIndex);
            mv.visitLdcInsn(value);
            emitStringOperator(mv);
            mv.visitInsn(IRETURN);

            mv.visitLabel(returnFalse);
            mv.visitInsn(ICONST_0);
            mv.visitInsn(IRETURN);
        }

        private void emitCookie(MethodVisitor mv, int baseLocalIndex) {
            int cookieHeaderIndex = baseLocalIndex;
            int cookiesSetIndex = baseLocalIndex + 1;
            int iteratorIndex = baseLocalIndex + 2;
            int cookieIndex = baseLocalIndex + 3;
            int cookieValueIndex = baseLocalIndex + 4;

            Label returnFalse = new Label();
            Label loopStart = new Label();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, HTTP_REQUEST, "headers",
                    "()Lio/netty/handler/codec/http/HttpHeaders;", true);
            mv.visitLdcInsn("Cookie");
            mv.visitMethodInsn(INVOKEVIRTUAL, HTTP_HEADERS, "get",
                    "(Ljava/lang/CharSequence;)Ljava/lang/String;", false);
            mv.visitVarInsn(ASTORE, cookieHeaderIndex);

            mv.visitVarInsn(ALOAD, cookieHeaderIndex);
            mv.visitJumpInsn(IFNULL, returnFalse);

            mv.visitFieldInsn(GETSTATIC, SERVER_COOKIE_DECODER, "STRICT",
                    "Lio/netty/handler/codec/http/cookie/ServerCookieDecoder;");
            mv.visitVarInsn(ALOAD, cookieHeaderIndex);
            mv.visitMethodInsn(INVOKEVIRTUAL, SERVER_COOKIE_DECODER, "decode",
                    "(Ljava/lang/String;)Ljava/util/Set;", false);
            mv.visitVarInsn(ASTORE, cookiesSetIndex);

            mv.visitVarInsn(ALOAD, cookiesSetIndex);
            mv.visitJumpInsn(IFNULL, returnFalse);

            mv.visitVarInsn(ALOAD, cookiesSetIndex);
            mv.visitMethodInsn(INVOKEINTERFACE, JAVA_UTIL_SET, "iterator", "()Ljava/util/Iterator;", true);
            mv.visitVarInsn(ASTORE, iteratorIndex);

            mv.visitLabel(loopStart);
            mv.visitVarInsn(ALOAD, iteratorIndex);
            mv.visitMethodInsn(INVOKEINTERFACE, JAVA_UTIL_ITERATOR, "hasNext", "()Z", true);
            mv.visitJumpInsn(IFEQ, returnFalse);

            mv.visitVarInsn(ALOAD, iteratorIndex);
            mv.visitMethodInsn(INVOKEINTERFACE, JAVA_UTIL_ITERATOR, "next", "()Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, NETTY_COOKIE);
            mv.visitVarInsn(ASTORE, cookieIndex);

            mv.visitVarInsn(ALOAD, cookieIndex);
            mv.visitMethodInsn(INVOKEINTERFACE, NETTY_COOKIE, "name", "()Ljava/lang/String;", true);
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_STRING, "equals", "(Ljava/lang/Object;)Z", false);
            mv.visitJumpInsn(IFEQ, loopStart);

            mv.visitVarInsn(ALOAD, cookieIndex);
            mv.visitMethodInsn(INVOKEINTERFACE, NETTY_COOKIE, "value", "()Ljava/lang/String;", true);
            mv.visitVarInsn(ASTORE, cookieValueIndex);

            mv.visitVarInsn(ALOAD, cookieValueIndex);
            mv.visitJumpInsn(IFNULL, returnFalse);

            mv.visitVarInsn(ALOAD, cookieValueIndex);
            mv.visitLdcInsn(value);
            emitStringOperator(mv);
            mv.visitInsn(IRETURN);

            mv.visitLabel(returnFalse);
            mv.visitInsn(ICONST_0);
            mv.visitInsn(IRETURN);
        }

        private void emitStringOperator(MethodVisitor mv) {
            switch (operator) {
                case EQUALS -> mv.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_STRING, "equals", "(Ljava/lang/Object;)Z", false);
                case CONTAINS -> mv.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_STRING, "contains", "(Ljava/lang/CharSequence;)Z", false);
                case STARTS_WITH -> mv.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_STRING, "startsWith", "(Ljava/lang/String;)Z", false);
                case ENDS_WITH -> mv.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_STRING, "endsWith", "(Ljava/lang/String;)Z", false);
            }
        }

        private enum LocationKind {
            HEADER(0),
            PARAMETER(4),
            COOKIE(5);

            private final int localSlots;

            LocationKind(int localSlots) {
                this.localSlots = localSlots;
            }

            static LocationKind from(IdentifierLocation location) {
                return switch (location) {
                    case HEADER, METADATA -> HEADER;
                    case QUERY_PARAM -> PARAMETER;
                    case COOKIE -> COOKIE;
                };
            }
        }
    }
}
