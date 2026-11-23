package com.kayo.reminder.application.component;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StartNotAfterDeadlineValidator.class)
public @interface StartNotAfterDeadline {

	String message();

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
