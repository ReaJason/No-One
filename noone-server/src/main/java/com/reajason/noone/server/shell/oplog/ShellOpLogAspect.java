package com.reajason.noone.server.shell.oplog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ShellOpLogAspect {

    private static final Set<Class<?>> EXCLUDED_PARAM_TYPES = Set.of(
            byte[].class
    );

    private final ApplicationEventPublisher eventPublisher;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(shellOpLog)")
    public Object around(ProceedingJoinPoint joinPoint, ShellOpLog shellOpLog) throws Throwable {
        long start = System.currentTimeMillis();
        String username = getCurrentUsername();
        Object result = null;
        boolean success = true;
        String errorMessage = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            try {
                Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
                var context = new MethodBasedEvaluationContext(
                        joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
                if (result != null) {
                    context.setVariable("result", result);
                }

                Long shellId = evaluateSpelAsLong(shellOpLog.shellId(), context);
                String pluginId = evaluateSpel(shellOpLog.pluginId(), context);
                String action = evaluateSpel(shellOpLog.action(), context);

                Map<String, Object> capturedArgs = captureArgs(joinPoint);
                Map<String, Object> capturedResult = wrapResult(result);

                eventPublisher.publishEvent(new ShellOperationLogEvent(
                        shellId, username, shellOpLog.operation(),
                        pluginId, action, capturedArgs, capturedResult,
                        success, errorMessage, durationMs
                ));
            } catch (Exception e) {
                log.warn("Failed to publish ShellOperationLogEvent", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> wrapResult(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        return Map.of("value", result);
    }

    private Map<String, Object> captureArgs(ProceedingJoinPoint joinPoint) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        Object[] values = joinPoint.getArgs();
        Class<?>[] types = sig.getParameterTypes();
        if (names == null || names.length == 0) {
            return Map.of();
        }
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            if (shouldExcludeParam(types[i])) {
                continue;
            }
            args.put(names[i], values[i]);
        }
        return args;
    }

    private boolean shouldExcludeParam(Class<?> type) {
        if (EXCLUDED_PARAM_TYPES.contains(type)) {
            return true;
        }
        return !isSerializableType(type);
    }

    private boolean isSerializableType(Class<?> type) {
        return type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type)
                || Boolean.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || Collection.class.isAssignableFrom(type)
                || type.isEnum();
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private String evaluateSpel(String expression, org.springframework.expression.EvaluationContext context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            Object value = parser.parseExpression(expression).getValue(context);
            return value != null ? String.valueOf(value) : null;
        } catch (Exception e) {
            log.debug("Failed to evaluate SpEL expression: {}", expression, e);
            return null;
        }
    }

    private Long evaluateSpelAsLong(String expression, org.springframework.expression.EvaluationContext context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            Object value = parser.parseExpression(expression).getValue(context);
            if (value instanceof Number number) {
                return number.longValue();
            }
            return value != null ? Long.parseLong(String.valueOf(value)) : null;
        } catch (Exception e) {
            log.debug("Failed to evaluate SpEL expression as Long: {}", expression, e);
            return null;
        }
    }
}
