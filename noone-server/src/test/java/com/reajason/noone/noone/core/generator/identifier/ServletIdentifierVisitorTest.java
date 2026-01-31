package com.reajason.noone.noone.core.generator.identifier;

import com.reajason.noone.core.generator.identifier.ServletIdentifierVisitor;
import com.reajason.noone.server.profile.config.IdentifierConfig;
import com.reajason.noone.server.profile.config.IdentifierLocation;
import com.reajason.noone.server.profile.config.IdentifierOperator;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class ServletIdentifierVisitorTest {
    private static final AtomicInteger TYPE_INDEX = new AtomicInteger(0);

    private static final class InstrumentedChecker {
        private final Object instance;
        private final Method isAuthedMethod;

        private InstrumentedChecker(Object instance, Method isAuthedMethod) {
            this.instance = instance;
            this.isAuthedMethod = isAuthedMethod;
        }

        boolean isAuthed(HttpServletRequest request) {
            try {
                return (boolean) isAuthedMethod.invoke(instance, request);
            } catch (IllegalAccessException e) {
                throw new AssertionError("Failed to invoke instrumented method", e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new AssertionError("Instrumented method threw", cause);
            }
        }
    }

    private static Stream<Arguments> operatorCases() {
        return Stream.of(
                Arguments.of(IdentifierOperator.EQUALS, "abc", "abc", true),
                Arguments.of(IdentifierOperator.EQUALS, "abc", "ab", false),
                Arguments.of(IdentifierOperator.CONTAINS, "xxabcxx", "abc", true),
                Arguments.of(IdentifierOperator.CONTAINS, "xxabxx", "abc", false),
                Arguments.of(IdentifierOperator.STARTS_WITH, "abcxx", "abc", true),
                Arguments.of(IdentifierOperator.STARTS_WITH, "xabc", "abc", false),
                Arguments.of(IdentifierOperator.ENDS_WITH, "xxabc", "abc", true),
                Arguments.of(IdentifierOperator.ENDS_WITH, "abcxx", "abc", false)
        );
    }

    @ParameterizedTest
    @MethodSource("operatorCases")
    void headerOperatorsWork(IdentifierOperator operator, String requestValue, String configValue, boolean expected) {
        InstrumentedChecker checker = instrument(new IdentifierConfig(IdentifierLocation.HEADER, operator, "X-Test", configValue));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Test")).thenReturn(requestValue);

        assertEquals(expected, checker.isAuthed(request));
        verify(request, times(2)).getHeader("X-Test");
    }

    @ParameterizedTest
    @MethodSource("operatorCases")
    void metadataOperatorsWork(IdentifierOperator operator, String requestValue, String configValue, boolean expected) {
        InstrumentedChecker checker = instrument(new IdentifierConfig(IdentifierLocation.METADATA, operator, "X-Test", configValue));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Test")).thenReturn(requestValue);

        assertEquals(expected, checker.isAuthed(request));
        verify(request, times(2)).getHeader("X-Test");
    }

    @Test
    void headerNullReturnsFalseAndCallsOnce() {
        InstrumentedChecker checker = instrument(new IdentifierConfig(IdentifierLocation.HEADER, IdentifierOperator.EQUALS, "X-Test", "abc"));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Test")).thenReturn(null);

        assertEquals(false, checker.isAuthed(request));
        verify(request, times(1)).getHeader("X-Test");
    }

    @ParameterizedTest
    @MethodSource("operatorCases")
    void queryParamOperatorsWork(IdentifierOperator operator, String requestValue, String configValue, boolean expected) {
        InstrumentedChecker checker = instrument(new IdentifierConfig(IdentifierLocation.QUERY_PARAM, operator, "p", configValue));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("p")).thenReturn(requestValue);

        assertEquals(expected, checker.isAuthed(request));
        verify(request, times(2)).getParameter("p");
    }

    @Test
    void queryParamNullReturnsFalseAndCallsOnce() {
        InstrumentedChecker checker = instrument(new IdentifierConfig(IdentifierLocation.QUERY_PARAM, IdentifierOperator.EQUALS, "p", "abc"));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("p")).thenReturn(null);

        assertEquals(false, checker.isAuthed(request));
        verify(request, times(1)).getParameter("p");
    }

    @ParameterizedTest
    @MethodSource("operatorCases")
    void cookieOperatorsWork(IdentifierOperator operator, String requestValue, String configValue, boolean expected) {
        InstrumentedChecker checker = instrument(new IdentifierConfig(IdentifierLocation.COOKIE, operator, "c", configValue));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("c", requestValue)});

        assertEquals(expected, checker.isAuthed(request));
        verify(request, times(1)).getCookies();
    }

    @Test
    void cookieArrayNullReturnsFalse() {
        InstrumentedChecker checker = instrument(new IdentifierConfig(IdentifierLocation.COOKIE, IdentifierOperator.EQUALS, "c", "abc"));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(null);

        assertEquals(false, checker.isAuthed(request));
    }

    @Test
    void cookieNameNotFoundReturnsFalse() {
        InstrumentedChecker checker = instrument(new IdentifierConfig(IdentifierLocation.COOKIE, IdentifierOperator.EQUALS, "c", "abc"));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("other", "abc")});

        assertEquals(false, checker.isAuthed(request));
    }

    @Test
    void cookieValueNullReturnsFalse() {
        InstrumentedChecker checker = instrument(new IdentifierConfig(IdentifierLocation.COOKIE, IdentifierOperator.EQUALS, "c", "abc"));

        Cookie cookie = mock(Cookie.class);
        when(cookie.getName()).thenReturn("c");
        when(cookie.getValue()).thenReturn(null);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        assertEquals(false, checker.isAuthed(request));
    }

    @Test
    void constructorRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new ServletIdentifierVisitor(null));
        assertThrows(NullPointerException.class, () -> new ServletIdentifierVisitor(new IdentifierConfig(null, IdentifierOperator.EQUALS, "n", "v")));
        assertThrows(NullPointerException.class, () -> new ServletIdentifierVisitor(new IdentifierConfig(IdentifierLocation.HEADER, null, "n", "v")));
        assertThrows(NullPointerException.class, () -> new ServletIdentifierVisitor(new IdentifierConfig(IdentifierLocation.HEADER, IdentifierOperator.EQUALS, null, "v")));
        assertThrows(NullPointerException.class, () -> new ServletIdentifierVisitor(new IdentifierConfig(IdentifierLocation.HEADER, IdentifierOperator.EQUALS, "n", null)));
    }

    private static InstrumentedChecker instrument(IdentifierConfig identifier) {
        int index = TYPE_INDEX.getAndIncrement();
        String baseName = ServletIdentifierVisitorTest.class.getName() + "$GeneratedAuthChecker$" + index;
        String instrumentedName = baseName + "$Instrumented";

        ClassLoader baseLoader = new SingleTypeClassLoader(
                ServletIdentifierVisitorTest.class.getClassLoader(),
                baseName,
                generateBaseCheckerBytes(baseName)
        );

        Class<?> baseType;
        try {
            baseType = Class.forName(baseName, true, baseLoader);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Failed to load base type", e);
        }

        Class<?> loaded = new ByteBuddy()
                .redefine(baseType)
                .name(instrumentedName)
                .method(named("isAuthed")
                        .and(takesArguments(HttpServletRequest.class))
                        .and(returns(boolean.class)))
                .intercept(new ServletIdentifierVisitor(identifier))
                .make()
                .load(baseLoader, ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();

        try {
            Object instance = loaded.getDeclaredConstructor().newInstance();
            Method method = loaded.getMethod("isAuthed", HttpServletRequest.class);
            return new InstrumentedChecker(instance, method);
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            throw new AssertionError("Failed to instantiate instrumented type", e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Instrumented constructor threw", e.getCause());
        }
    }

    private static byte[] generateBaseCheckerBytes(String binaryName) {
        String internalName = binaryName.replace('.', '/');

        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);

        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor isAuthed = cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                "isAuthed",
                "(Ljavax/servlet/http/HttpServletRequest;)Z",
                null,
                null
        );
        isAuthed.visitCode();
        isAuthed.visitInsn(Opcodes.ICONST_0);
        isAuthed.visitInsn(Opcodes.IRETURN);
        isAuthed.visitMaxs(1, 2);
        isAuthed.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static final class SingleTypeClassLoader extends ClassLoader {
        private final String typeName;
        private final byte[] typeBytes;
        private final String typeResourceName;

        private SingleTypeClassLoader(ClassLoader parent, String typeName, byte[] typeBytes) {
            super(parent);
            this.typeName = typeName;
            this.typeBytes = typeBytes;
            this.typeResourceName = typeName.replace('.', '/') + ".class";
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.equals(typeName)) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, typeBytes, 0, typeBytes.length);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (name.equals(typeResourceName)) {
                return new ByteArrayInputStream(typeBytes);
            }
            return super.getResourceAsStream(name);
        }
    }
}
