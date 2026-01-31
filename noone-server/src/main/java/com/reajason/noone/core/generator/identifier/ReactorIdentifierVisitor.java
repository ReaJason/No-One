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

import static net.bytebuddy.jar.asm.Opcodes.*;

/**
 * Identifier visitor for Spring WebFlux ServerHttpRequest.
 * Supports header, query parameter, and cookie based authentication.
 */
public final class ReactorIdentifierVisitor implements Implementation {
    private final IdentifierLocation location;
    private final IdentifierOperator operator;
    private final String name;
    private final String value;

    public ReactorIdentifierVisitor(IdentifierConfig identifier) {
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
        private static final String SERVER_HTTP_REQUEST = "org/springframework/http/server/reactive/ServerHttpRequest";
        private static final String HTTP_HEADERS = "org/springframework/http/HttpHeaders";
        private static final String MULTI_VALUE_MAP = "org/springframework/util/MultiValueMap";
        private static final String HTTP_COOKIE = "org/springframework/http/HttpCookie";
        private static final String JAVA_LANG_STRING = "java/lang/String";

        @Override
        public Size apply(MethodVisitor methodVisitor, Context context, MethodDescription methodDescription) {
            LocationKind locationKind = LocationKind.from(location);

            switch (locationKind) {
                case HEADER -> emitHeader(methodVisitor);
                case PARAMETER -> emitParameter(methodVisitor);
                case COOKIE -> emitCookie(methodVisitor);
            }

            return new Size(8, methodDescription.getStackSize() + locationKind.localSlots);
        }

        private void emitHeader(MethodVisitor mv) {
            Label returnFalse = new Label();

            // request.getHeaders()
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, SERVER_HTTP_REQUEST, "getHeaders",
                    "()Lorg/springframework/http/HttpHeaders;", true);

            // headers.getFirst(name)
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(INVOKEVIRTUAL, HTTP_HEADERS, "getFirst",
                    "(Ljava/lang/String;)Ljava/lang/String;", false);

            // null check
            mv.visitJumpInsn(IFNULL, returnFalse);

            // request.getHeaders().getFirst(name) again for comparison
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, SERVER_HTTP_REQUEST, "getHeaders",
                    "()Lorg/springframework/http/HttpHeaders;", true);
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(INVOKEVIRTUAL, HTTP_HEADERS, "getFirst",
                    "(Ljava/lang/String;)Ljava/lang/String;", false);

            mv.visitLdcInsn(value);
            emitStringOperator(mv);
            mv.visitInsn(IRETURN);

            mv.visitLabel(returnFalse);
            mv.visitInsn(ICONST_0);
            mv.visitInsn(IRETURN);
        }

        private void emitParameter(MethodVisitor mv) {
            Label returnFalse = new Label();

            // request.getQueryParams()
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, SERVER_HTTP_REQUEST, "getQueryParams",
                    "()Lorg/springframework/util/MultiValueMap;", true);

            // queryParams.getFirst(name)
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(INVOKEINTERFACE, MULTI_VALUE_MAP, "getFirst",
                    "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, JAVA_LANG_STRING);

            // null check
            mv.visitJumpInsn(IFNULL, returnFalse);

            // request.getQueryParams().getFirst(name) again for comparison
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, SERVER_HTTP_REQUEST, "getQueryParams",
                    "()Lorg/springframework/util/MultiValueMap;", true);
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(INVOKEINTERFACE, MULTI_VALUE_MAP, "getFirst",
                    "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, JAVA_LANG_STRING);

            mv.visitLdcInsn(value);
            emitStringOperator(mv);
            mv.visitInsn(IRETURN);

            mv.visitLabel(returnFalse);
            mv.visitInsn(ICONST_0);
            mv.visitInsn(IRETURN);
        }

        private void emitCookie(MethodVisitor mv) {
            Label returnFalse = new Label();

            // request.getCookies()
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, SERVER_HTTP_REQUEST, "getCookies",
                    "()Lorg/springframework/util/MultiValueMap;", true);

            // cookies.getFirst(name)
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(INVOKEINTERFACE, MULTI_VALUE_MAP, "getFirst",
                    "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, HTTP_COOKIE);

            // null check
            mv.visitJumpInsn(IFNULL, returnFalse);

            // request.getCookies().getFirst(name).getValue() for comparison
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, SERVER_HTTP_REQUEST, "getCookies",
                    "()Lorg/springframework/util/MultiValueMap;", true);
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(INVOKEINTERFACE, MULTI_VALUE_MAP, "getFirst",
                    "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, HTTP_COOKIE);
            mv.visitMethodInsn(INVOKEVIRTUAL, HTTP_COOKIE, "getValue",
                    "()Ljava/lang/String;", false);

            // null check on cookie value
            mv.visitJumpInsn(IFNULL, returnFalse);

            // Get cookie value again for comparison
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, SERVER_HTTP_REQUEST, "getCookies",
                    "()Lorg/springframework/util/MultiValueMap;", true);
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(INVOKEINTERFACE, MULTI_VALUE_MAP, "getFirst",
                    "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, HTTP_COOKIE);
            mv.visitMethodInsn(INVOKEVIRTUAL, HTTP_COOKIE, "getValue",
                    "()Ljava/lang/String;", false);

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
            PARAMETER(0),
            COOKIE(0);

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
