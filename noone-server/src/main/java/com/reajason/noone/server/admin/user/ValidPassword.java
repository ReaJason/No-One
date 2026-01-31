package com.reajason.noone.server.admin.user;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordConstraintValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {
    String message() default "密码复杂度不符合要求";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}