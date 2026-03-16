package com.reajason.noone.server.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
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
                EvaluationContext context = new MethodBasedEvaluationContext(
                        joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
                if (result != null) {
                    context.setVariable("result", result);
                }

                String targetId = evaluateSpel(auditLog.targetId(), context);
                String description = evaluateSpel(auditLog.description(), context);

                auditLogService.record(AuditLogService.AuditEntry.builder()
                        .module(auditLog.module())
                        .action(auditLog.action())
                        .targetType(auditLog.targetType())
                        .targetId(targetId)
                        .description(description)
                        .success(success)
                        .errorMessage(errorMessage)
                        .durationMs(durationMs)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to record audit log via AOP", e);
            }
        }
    }

    private String evaluateSpel(String expression, EvaluationContext context) {
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
}
