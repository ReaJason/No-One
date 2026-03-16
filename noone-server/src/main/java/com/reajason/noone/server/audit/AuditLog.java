package com.reajason.noone.server.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    AuditModule module();

    AuditAction action();

    /**
     * SpEL expression to resolve the target ID from method arguments.
     * e.g. "#id", "#request.id", "#result.id"
     */
    String targetId() default "";

    String targetType() default "";

    /**
     * SpEL expression for description.
     * e.g. "'Created user ' + #request.username"
     */
    String description() default "";
}
