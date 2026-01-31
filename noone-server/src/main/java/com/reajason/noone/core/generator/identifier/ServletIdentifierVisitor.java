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

public final class ServletIdentifierVisitor implements Implementation {
    private final IdentifierLocation location;
    private final IdentifierOperator operator;
    private final String name;
    private final String value;

    public ServletIdentifierVisitor(IdentifierConfig identifier) {
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
            private static final String HTTP_SERVLET_REQUEST = "javax/servlet/http/HttpServletRequest";
            private static final String COOKIE = "javax/servlet/http/Cookie";
            private static final String JAVA_LANG_STRING = "java/lang/String";

        @Override
            public Size apply(MethodVisitor methodVisitor, Context context, MethodDescription methodDescription) {
                LocationKind locationKind = LocationKind.from(location);

                switch (locationKind) {
                    case HEADER -> emitHeaderOrMetadata(methodVisitor);
                    case PARAMETER -> emitParameter(methodVisitor);
                    case COOKIE -> emitCookie(methodVisitor, methodDescription.getStackSize());
                }

                return new Size(8, methodDescription.getStackSize() + locationKind.localSlots);
            }

            private void emitHeaderOrMetadata(MethodVisitor mv) {
                emitGetterNullCheckAndMatch(mv, "getHeader");
            }

            private void emitParameter(MethodVisitor mv) {
                emitGetterNullCheckAndMatch(mv, "getParameter");
            }

            private void emitGetterNullCheckAndMatch(MethodVisitor mv, String getterName) {
                Label returnFalse = new Label();

                mv.visitVarInsn(ALOAD, 1);
                mv.visitLdcInsn(name);
                mv.visitMethodInsn(INVOKEINTERFACE, HTTP_SERVLET_REQUEST, getterName, "(Ljava/lang/String;)Ljava/lang/String;", true);
                mv.visitJumpInsn(IFNULL, returnFalse);

                mv.visitVarInsn(ALOAD, 1);
                mv.visitLdcInsn(name);
                mv.visitMethodInsn(INVOKEINTERFACE, HTTP_SERVLET_REQUEST, getterName, "(Ljava/lang/String;)Ljava/lang/String;", true);
                mv.visitLdcInsn(value);
                emitStringOperator(mv);
                mv.visitInsn(IRETURN);

                mv.visitLabel(returnFalse);
                mv.visitInsn(ICONST_0);
                mv.visitInsn(IRETURN);
            }

            private void emitCookie(MethodVisitor mv, int baseLocalIndex) {
                int cookiesIndex = baseLocalIndex;
                int indexIndex = baseLocalIndex + 1;
                int cookieIndex = baseLocalIndex + 2;
                int cookieValueIndex = baseLocalIndex + 3;

                Label returnFalse = new Label();
                Label loopStart = new Label();
                Label loopEnd = new Label();
                Label nextIter = new Label();
                Label cookieFound = new Label();

                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEINTERFACE, HTTP_SERVLET_REQUEST, "getCookies", "()[Ljavax/servlet/http/Cookie;", true);
                mv.visitVarInsn(ASTORE, cookiesIndex);

                mv.visitVarInsn(ALOAD, cookiesIndex);
                mv.visitJumpInsn(IFNULL, returnFalse);

                mv.visitInsn(ICONST_0);
                mv.visitVarInsn(ISTORE, indexIndex);

                mv.visitLabel(loopStart);
                mv.visitVarInsn(ILOAD, indexIndex);
                mv.visitVarInsn(ALOAD, cookiesIndex);
                mv.visitInsn(ARRAYLENGTH);
                mv.visitJumpInsn(IF_ICMPGE, loopEnd);

                mv.visitVarInsn(ALOAD, cookiesIndex);
                mv.visitVarInsn(ILOAD, indexIndex);
                mv.visitInsn(AALOAD);
                mv.visitVarInsn(ASTORE, cookieIndex);

                mv.visitLdcInsn(name);
                mv.visitVarInsn(ALOAD, cookieIndex);
                mv.visitMethodInsn(INVOKEVIRTUAL, COOKIE, "getName", "()Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_STRING, "equals", "(Ljava/lang/Object;)Z", false);
                mv.visitJumpInsn(IFNE, cookieFound);

                mv.visitLabel(nextIter);
                mv.visitIincInsn(indexIndex, 1);
                mv.visitJumpInsn(GOTO, loopStart);

                mv.visitLabel(cookieFound);
                mv.visitVarInsn(ALOAD, cookieIndex);
                mv.visitMethodInsn(INVOKEVIRTUAL, COOKIE, "getValue", "()Ljava/lang/String;", false);
                mv.visitVarInsn(ASTORE, cookieValueIndex);

                mv.visitVarInsn(ALOAD, cookieValueIndex);
                mv.visitJumpInsn(IFNULL, returnFalse);

                mv.visitVarInsn(ALOAD, cookieValueIndex);
                mv.visitLdcInsn(value);
                emitStringOperator(mv);
                mv.visitInsn(IRETURN);

                mv.visitLabel(loopEnd);
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
                COOKIE(4);

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
