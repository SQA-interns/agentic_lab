package org.example.conference.shared.text;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Required email address with backend-authoritative syntax (see {@link InputPatterns#EMAIL}). */
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@NotBlank
@Size(max = InputPatterns.MAX_EMAIL)
@Pattern(regexp = InputPatterns.EMAIL)
public @interface RequiredEmail {

  String message() default "invalid email";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
