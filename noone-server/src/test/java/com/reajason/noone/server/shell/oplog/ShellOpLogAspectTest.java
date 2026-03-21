package com.reajason.noone.server.shell.oplog;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShellOpLogAspectTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private ShellOpLogAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new ShellOpLogAspect(eventPublisher);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", null));
    }

    @Test
    void shouldPublishEventOnSuccess() throws Throwable {
        ShellOpLog annotation = mock(ShellOpLog.class);
        when(annotation.operation()).thenReturn(ShellOperationType.TEST);
        when(annotation.shellId()).thenReturn("#id");
        when(annotation.pluginId()).thenReturn("");
        when(annotation.action()).thenReturn("");

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{42L});
        when(joinPoint.proceed()).thenReturn(true);

        Method method = SampleService.class.getMethod("testMethod", Long.class);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterTypes()).thenReturn(new Class[]{Long.class});

        Object result = aspect.around(joinPoint, annotation);
        assertEquals(true, result);

        ArgumentCaptor<ShellOperationLogEvent> captor = ArgumentCaptor.forClass(ShellOperationLogEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        ShellOperationLogEvent event = captor.getValue();
        assertEquals(42L, event.shellId());
        assertEquals("admin", event.username());
        assertEquals(ShellOperationType.TEST, event.operation());
        assertTrue(event.success());
        assertNull(event.errorMessage());
        assertEquals(Map.of("value", true), event.result());
    }

    @Test
    void shouldPublishEventWithErrorOnException() throws Throwable {
        ShellOpLog annotation = mock(ShellOpLog.class);
        when(annotation.operation()).thenReturn(ShellOperationType.DISPATCH);
        when(annotation.shellId()).thenReturn("#shellId");
        when(annotation.pluginId()).thenReturn("#pluginId");
        when(annotation.action()).thenReturn("");

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, "system-info"});
        when(joinPoint.proceed()).thenThrow(new RuntimeException("connection refused"));

        Method method = SampleService.class.getMethod("dispatchMethod", Long.class, String.class);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterTypes()).thenReturn(new Class[]{Long.class, String.class});

        assertThrows(RuntimeException.class, () -> aspect.around(joinPoint, annotation));

        ArgumentCaptor<ShellOperationLogEvent> captor = ArgumentCaptor.forClass(ShellOperationLogEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        ShellOperationLogEvent event = captor.getValue();
        assertEquals(1L, event.shellId());
        assertFalse(event.success());
        assertEquals("connection refused", event.errorMessage());
    }

    public static class SampleService {
        public boolean testMethod(Long id) { return true; }
        public Map<String, Object> dispatchMethod(Long shellId, String pluginId) { return Map.of(); }
    }
}
