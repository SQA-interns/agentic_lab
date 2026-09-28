package org.example.conference.shared.text;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Required participant text field: non-blank after trimming, bounded length, no control characters.
 * Each composing constraint reports its own violation code.
 */
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@NotBlank
@Size
@Pattern(regexp = InputPatterns.NO_CONTROL_CHARS)
public @interface RequiredText {

  @OverridesAttribute(constraint = Size.class, name = "max")
  int max() default InputPatterns.MAX_LONG_TEXT;

  String message() default "invalid text";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
