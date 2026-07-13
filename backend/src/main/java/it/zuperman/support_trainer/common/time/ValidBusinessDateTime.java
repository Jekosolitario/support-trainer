package it.zuperman.support_trainer.common.time;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = BusinessDateTimeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBusinessDateTime {

    String message() default "La data e ora non sono valide per la zona business";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
