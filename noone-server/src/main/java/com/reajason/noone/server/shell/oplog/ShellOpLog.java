package com.reajason.noone.server.shell.oplog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShellOpLog {
    ShellOperationType operation();
    String pluginId() default "";
    String action() default "";
    String shellId() default "";
}
